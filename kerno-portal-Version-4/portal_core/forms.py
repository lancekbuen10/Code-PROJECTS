# portal_core/forms.py
from django.contrib.auth.forms import UserCreationForm, AuthenticationForm
from django import forms
from .models import CustomUser

# Registration Form for new users
class EmployeeRegistrationForm(UserCreationForm):
    class Meta(UserCreationForm.Meta):
        model = CustomUser
        fields = ('username', 'email', 'department')

# Admin Edit Form (with role management and checkbox styling)
class EmployeeEditForm(forms.ModelForm):
    class Meta:
        model = CustomUser
        fields = ('username', 'email', 'department', 'is_approved', 'is_staff', 'is_superuser')
        
    def __init__(self, *args, **kwargs):
        super(EmployeeEditForm, self).__init__(*args, **kwargs)
        # Apply portal-themed classes to all checkbox inputs automatically
        for field in self.fields:
            if isinstance(self.fields[field].widget, forms.CheckboxInput):
                self.fields[field].widget.attrs.update({
                    'class': 'h-4 w-4 text-kerno-green bg-gray-800 border-gray-600 rounded focus:ring-kerno-green'
                })

# Custom Authentication Form for specific error messaging
class PortalAuthenticationForm(AuthenticationForm):
    error_messages = {
        'invalid_login': "These credentials are unfamiliar. Please try again.",
    }
