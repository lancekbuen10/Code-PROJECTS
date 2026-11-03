# portal_core/urls.py
from django.urls import path
from django.contrib.auth import views as auth_views
from . import views
from .forms import PortalAuthenticationForm

urlpatterns = [
    # Dashboard & Main Entry
    path('', views.dashboard_view, name='dashboard'),
    
    # Tool Suite (Navigation Menu)
    path('tools/', views.tools_dashboard_view, name='tools_dashboard'),
    
    # Step-by-Step Workflow Routes
    path('tool/bom-modifier/', views.bom_modifier_view, name='bom_modifier'),
    path('tool/step1-validate/', views.step1_validate_view, name='step1_validate'),
    path('tool/step2-generate/', views.step2_generate_view, name='step2_generate'),
    path('tool/step2-combine/', views.step2_combine_view, name='step2_combine'),
    path('tool/step3-final/', views.step3_final_view, name='step3_final'),
    
    # Activity Logs (Staff Access Only)
    path('logs/', views.activity_log_view, name='activity_logs'),
    
    # Administrative Actions (User Management)
    path('approve/<int:user_id>/', views.approve_employee_view, name='approve_employee'),
    path('manage/<int:user_id>/', views.manage_employee_view, name='manage_employee'),
    path('delete/<int:user_id>/', views.delete_employee_view, name='delete_employee'),
    
    # Authentication (Login/Logout/Register)
    path('register/', views.register_view, name='register'),
    path('login/', auth_views.LoginView.as_view(
        template_name='portal_core/login.html', 
        authentication_form=PortalAuthenticationForm
    ), name='login'),
    path('logout/', auth_views.LogoutView.as_view(), name='logout'),
]
