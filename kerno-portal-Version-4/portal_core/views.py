# portal_core/views.py
import os
import io
import re
import traceback
import zipfile
from django.conf import settings
from django.http import HttpResponse
from django.shortcuts import render, redirect, get_object_or_404
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.contrib.auth.forms import SetPasswordForm
from .forms import EmployeeRegistrationForm, EmployeeEditForm
from .models import CustomUser, GenerationLog
from .utils import (
    modify_bom_format,
    validate_and_match_files,
    generate_feeder_list,
    combine_feeder_lists,
    generate_final_wi_kerno
)

# ==========================================
# AUTHENTICATION & RBAC VIEWS
# ==========================================

def register_view(request):
    if request.method == 'POST':
        form = EmployeeRegistrationForm(request.POST)
        if form.is_valid():
            new_user = form.save(commit=False)
            new_user.is_employee = True
            new_user.is_approved = False
            new_user.is_active = True
            new_user.save() 
            messages.success(request, "Registration successful! Awaiting Admin approval.")
            return redirect('login') 
    else:
        form = EmployeeRegistrationForm()
    return render(request, 'portal_core/register.html', {'form': form})

@login_required
def dashboard_view(request):
    context = {}
    if request.user.is_staff:
        context['pending_users'] = CustomUser.objects.filter(is_employee=True, is_approved=False)
    return render(request, 'portal_core/dashboard.html', context)

@login_required
def approve_employee_view(request, user_id):
    if not request.user.is_staff:
        messages.error(request, "Unauthorized.")
        return redirect('dashboard')
    user_to_approve = get_object_or_404(CustomUser, id=user_id)
    user_to_approve.is_approved = True
    user_to_approve.save()
    messages.success(request, f"User {user_to_approve.username} approved.")
    return redirect('dashboard')

@login_required
def manage_employee_view(request, user_id):
    if not request.user.is_staff:
        messages.error(request, "Unauthorized.")
        return redirect('dashboard')
    target_user = get_object_or_404(CustomUser, id=user_id)
    if request.method == 'POST':
        if 'reset_password' in request.POST:
            form = SetPasswordForm(target_user, request.POST)
            if form.is_valid():
                form.save()
                messages.success(request, f"Password reset for {target_user.username}.")
                return redirect('dashboard')
        else:
            form = EmployeeEditForm(request.POST, instance=target_user)
            if form.is_valid():
                form.save()
                messages.success(request, f"Updated {target_user.username}.")
                return redirect('dashboard')
    else:
        form = EmployeeEditForm(instance=target_user)
        reset_form = SetPasswordForm(target_user)
    return render(request, 'portal_core/manage_employee.html', {'form': form, 'reset_form': reset_form, 'target_user': target_user})

@login_required
def delete_employee_view(request, user_id):
    if not request.user.is_staff:
        return redirect('dashboard')
    target_user = get_object_or_404(CustomUser, id=user_id)
    if request.method == 'POST':
        target_user.delete()
        messages.success(request, "User deleted.")
    return redirect('dashboard')

@login_required
def tools_dashboard_view(request):
    if not request.user.is_approved:
        messages.error(request, "Awaiting admin approval.")
        return redirect('dashboard')
    return render(request, 'portal_core/tools_dashboard.html')


# ==========================================
# TOOL PIPELINE VIEWS
# ==========================================

@login_required
def bom_modifier_view(request):
    """Tool 0: BOM Modifier (Module 5)"""
    if request.method == 'POST':
        bom_file = request.FILES.get('bom_file')
        if bom_file:
            try:
                res = modify_bom_format(bom_file)
                GenerationLog.objects.create(
                    user=request.user, 
                    action="BOM Modification", 
                    details=f"Source BOM: {bom_file.name}"
                )
                response = HttpResponse(
                    res['file_buffer'].getvalue(), 
                    content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                )
                response['Content-Disposition'] = f'attachment; filename="{res["filename"]}"'
                messages.success(request, "BOM Successfully Modified!")
                return response
            except Exception as e:
                print("--- BOM MODIFIER CRASHED ---")
                traceback.print_exc()
                messages.error(request, f"Error processing file: {str(e)}")
        else:
            messages.error(request, "Please upload a BOM file.")
    return render(request, 'portal_core/bom_modifier.html')


@login_required
def step1_validate_view(request):
    """Step 1: BOM vs Combined Placement Validation."""
    if request.method == 'POST':
        mode = request.POST.get('mode', 'double')
        bom = request.FILES.get('bom_file')
        
        pl_t = None
        pl_b = None
        
        if mode == 'double':
            pl_t = request.FILES.get('placement_t')
            pl_b = request.FILES.get('placement_b')
            if not (bom and pl_t and pl_b):
                messages.error(request, "Please upload all required files (BOM, Top, Bottom).")
                return render(request, 'portal_core/step1_validate.html')
            
        elif mode == 'single':
            pl_single = request.FILES.get('placement_single')
            if not (bom and pl_single):
                messages.error(request, "Please upload the BOM and placement file.")
                return render(request, 'portal_core/step1_validate.html')
            
            pl_t = pl_single
            pl_b = None
            
        else:
            messages.error(request, "Invalid validation mode selected.")
            return render(request, 'portal_core/step1_validate.html')

        try:
            res = validate_and_match_files(bom, pl_t, pl_b)
            
            GenerationLog.objects.create(
                user=request.user, 
                action=f"Step 1 Validation ({mode.title()} Side)", 
                details=f"BOM: {bom.name}"
            )
            
            response = HttpResponse(
                res['file_buffer'].getvalue(), 
                content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            )
            
            output_filename = res.get('filename', 'BOM_Consolidated_Report.xlsx')
            response['Content-Disposition'] = f'attachment; filename="{output_filename}"'
            
            if res['is_valid']:
                messages.success(request, "Validation Passed!")
            else:
                messages.warning(request, "Validation completed with missing items.")
            return response
            
        except Exception as e:
            print("--- VALIDATION CRASHED ---")
            traceback.print_exc() 
            messages.error(request, f"Error processing file: {str(e)}")
            
    return render(request, 'portal_core/step1_validate.html')


def _identify_step2_file_pair(uploaded_files, side):
    """Return the Placement and Feeder Setup CSVs selected for one PCB side."""
    side_name = "Bottom" if side.upper() == "B" else "Top"
    side_letter = side.lower()

    if len(uploaded_files) != 2:
        raise ValueError(
            f"Select exactly two {side_name} CSV files together: "
            f"Placement_{side.upper()} and FeederSetup_{side.upper()}."
        )

    identified = {}
    for uploaded_file in uploaded_files:
        filename = os.path.basename(uploaded_file.name)
        stem, extension = os.path.splitext(filename)
        if extension.lower() != ".csv":
            raise ValueError(f"{filename} is not a CSV file.")

        lowered_stem = stem.lower()
        compact_stem = re.sub(r"[^a-z0-9]+", "", lowered_stem)
        name_tokens = re.findall(r"[a-z0-9]+", lowered_stem)

        has_correct_side = (
            side_name.lower() in compact_stem
            or side_letter in name_tokens
            or compact_stem.endswith(side_letter)
        )
        if not has_correct_side:
            raise ValueError(
                f"{filename} is not named for the {side_name} side. "
                f"Use Placement_{side.upper()} and FeederSetup_{side.upper()}."
            )

        if "placement" in compact_stem:
            file_role = "placement"
        elif "feeder" in compact_stem and "setup" in compact_stem:
            file_role = "setup"
        else:
            raise ValueError(
                f"Cannot identify {filename}. Use filenames such as "
                f"Placement_{side.upper()}.csv and FeederSetup_{side.upper()}.csv."
            )

        if file_role in identified:
            raise ValueError(
                f"Two {file_role.replace('_', ' ')} files were selected for "
                f"{side_name}. Select one Placement file and one Feeder Setup file."
            )
        identified[file_role] = uploaded_file

    if "placement" not in identified or "setup" not in identified:
        raise ValueError(
            f"The {side_name} selection must contain Placement_{side.upper()} "
            f"and FeederSetup_{side.upper()}."
        )

    return identified["placement"], identified["setup"]


@login_required
def step2_generate_view(request):
    if request.method == 'POST':
        bottom_files = request.FILES.getlist('bottom_files')
        top_files = request.FILES.getlist('top_files')
        minmax = request.FILES.get('minmax_db')

        if bottom_files and top_files and minmax:
            try:
                bottom_placement, bottom_setup = _identify_step2_file_pair(
                    bottom_files, 'B'
                )
                top_placement, top_setup = _identify_step2_file_pair(
                    top_files, 'T'
                )

                bottom_result = generate_feeder_list(
                    bottom_placement, bottom_setup, minmax, side='Bottom'
                )
                top_result = generate_feeder_list(
                    top_placement, top_setup, minmax, side='Top'
                )

                zip_buffer = io.BytesIO()
                with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as archive:
                    archive.writestr(
                        'Feeder_List_B.xlsx',
                        bottom_result['file_buffer'].getvalue(),
                    )
                    archive.writestr(
                        'Feeder_List_T.xlsx',
                        top_result['file_buffer'].getvalue(),
                    )
                zip_buffer.seek(0)

                GenerationLog.objects.create(
                    user=request.user,
                    action="Step 2a Generation",
                    details=(
                        f"Bottom: {bottom_placement.name} + {bottom_setup.name}; "
                        f"Top: {top_placement.name} + {top_setup.name}"
                    ),
                )
                response = HttpResponse(
                    zip_buffer.getvalue(),
                    content_type='application/zip',
                )
                response['Content-Disposition'] = 'attachment; filename="Feeder_Lists_B_and_T.zip"'
                return response
            except Exception as e:
                print("--- STEP 2A CRASHED ---")
                traceback.print_exc()
                messages.error(request, f"Error: {str(e)}")
        else:
            messages.error(request, "Select the two Bottom files, the two Top files, and the Min/Max database.")
    return render(request, 'portal_core/step2_generate.html')

@login_required
def step2_combine_view(request):
    if request.method == 'POST':
        top = request.FILES.get('top_feeder_file')
        bot = request.FILES.get('bottom_feeder_file')
        if top and bot:
            try:
                buffer = combine_feeder_lists(top, bot)
                GenerationLog.objects.create(user=request.user, action="Step 2b Combine", details="Combined T/B lists")
                response = HttpResponse(
                    buffer.getvalue(),
                    content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                )
                response['Content-Disposition'] = 'attachment; filename="Feeder_List_Combined.xlsx"'
                return response
            except Exception as e:
                traceback.print_exc()
                messages.error(request, f"Error: {str(e)}")
        else:
            messages.error(request, "Please upload Top and Bottom Feeder Lists.")
    return render(request, 'portal_core/step2_combine.html')

@login_required
def step3_final_view(request):
    if request.method == 'POST':
        combined = request.FILES.get('combined_file')
        template = request.FILES.get('template_file')
        
        if combined and template:
            try:
                result = generate_final_wi_kerno(combined, template)
                GenerationLog.objects.create(user=request.user, action="Step 3 Final", details="Generated WI")
                
                if isinstance(result, dict):
                    file_content = result['file_buffer'].getvalue()
                    filename = result.get('filename', 'NXT_Device_Loading_List.xlsx')
                else:
                    file_content = result.getvalue()
                    filename = 'NXT_Device_Loading_List.xlsx'
                
                response = HttpResponse(
                    file_content,
                    content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                )
                response['Content-Disposition'] = f'attachment; filename="{filename}"'
                return response
            except Exception as e:
                print("\n--- STEP 3 FINAL CRASHED ---")
                traceback.print_exc()
                print("----------------------------\n")
                messages.error(request, f"Error: {str(e)}")
        else:
            messages.error(request, "Please upload both the Combined Feeder List and the Template file.")
            
    return render(request, 'portal_core/step3_final.html')
    
@login_required
def activity_log_view(request):
    if not request.user.is_staff:
        messages.error(request, "Only staff can view activity logs.")
        return redirect('dashboard')
    logs = GenerationLog.objects.all().order_by('-timestamp')
    return render(request, 'portal_core/activity_logs.html', {'logs': logs})
