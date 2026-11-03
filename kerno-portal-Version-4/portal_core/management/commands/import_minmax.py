import pandas as pd
from django.core.management.base import BaseCommand
from portal_core.models import PartParameter

class Command(BaseCommand):
    help = 'Imports the MinMax Excel database into the portal database'

    def add_arguments(self, parser):
        parser.add_argument('file_path', type=str, help='Path to the minmax.xlsx file')

    def handle(self, *args, **kwargs):
        file_path = kwargs['file_path']
        
        self.stdout.write(self.style.WARNING(f'Loading data from {file_path}...'))
        
        try:
            # Read the Excel file using pandas
            df = pd.read_excel(file_path)
            
            success_count = 0
            
            # Loop through every row in the spreadsheet
            for index, row in df.iterrows():
                # Based on the macro: Col 1 is PartNumber (index 0)
                part_no = str(row.iloc[0]).strip()
                
                if part_no and part_no != 'nan':
                    # Extract the other columns, handling blank cells safely
                    desc = str(row.iloc[2]).strip() if pd.notna(row.iloc[2]) else ''
                    min_val = str(row.iloc[9]).strip() if pd.notna(row.iloc[9]) else ''
                    max_val = str(row.iloc[10]).strip() if pd.notna(row.iloc[10]) else ''
                    
                    # update_or_create prevents duplicate errors if run multiple times
                    PartParameter.objects.update_or_create(
                        part_number=part_no,
                        defaults={
                            'description': desc,
                            'min_unit': min_val,
                            'max_unit': max_val
                        }
                    )
                    success_count += 1
                    
            self.stdout.write(self.style.SUCCESS(f'SUCCESS: Imported {success_count} parts into the portal database!'))
            
        except Exception as e:
            self.stdout.write(self.style.ERROR(f'ERROR: {str(e)}'))
