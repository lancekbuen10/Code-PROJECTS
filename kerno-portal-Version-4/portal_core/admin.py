# portal_core/admin.py
from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from .models import CustomUser

class CustomUserAdmin(UserAdmin):
    model = CustomUser
    
    # This adds our custom fields to the user editing screen
    fieldsets = UserAdmin.fieldsets + (
        ('Enterprise Flags', {'fields': ('is_employee', 'is_approved', 'department')}),
    )
    
    # This adds columns to the main user list view so you can see status at a glance
    list_display = ['username', 'email', 'is_employee', 'is_approved', 'is_staff']

# Register the model and the admin class
admin.site.register(CustomUser, CustomUserAdmin)
