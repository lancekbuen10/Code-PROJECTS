# portal_core/models.py
from django.db import models
from django.contrib.auth.models import AbstractUser

# 1. Your Custom User for Authentication
class CustomUser(AbstractUser):
    is_employee = models.BooleanField(default=False)
    is_approved = models.BooleanField(default=False)
    # Added the department field to resolve the FieldError
    department = models.CharField(max_length=100, blank=True, null=True)

# 2. Your Factory MinMax Database Table
class PartParameter(models.Model):
    part_number = models.CharField(max_length=100, unique=True)
    description = models.CharField(max_length=255, blank=True, null=True)
    min_unit = models.CharField(max_length=50, blank=True, null=True)
    max_unit = models.CharField(max_length=50, blank=True, null=True)

    def __str__(self):
        return self.part_number

# 3. The Audit Trail Table
class GenerationLog(models.Model):
    user = models.ForeignKey(CustomUser, on_delete=models.SET_NULL, null=True, blank=True)
    action = models.CharField(max_length=255)
    details = models.TextField()
    timestamp = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.user} - {self.action} at {self.timestamp}"