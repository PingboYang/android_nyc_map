import geopandas as gpd
from io import StringIO

# Function to export GeoDataFrame to an SQL file
def gdf_to_sql_file(gdf, table_name, sql_file_path):
    # Create a string buffer to hold the SQL commands
    sql_buffer = StringIO()

    # Generate the CREATE TABLE statement
    sql_buffer.write(f"CREATE TABLE {table_name} (\n")
    sql_buffer.write("  id SERIAL PRIMARY KEY,\n")
    sql_buffer.write("  geom GEOMETRY(LineString, 4326),\n")
    
    # Add columns based on the properties of the shapefile
    for i, col in enumerate(gdf.columns):
        if col != 'geometry':  # 'geometry' is already handled separately
            sql_buffer.write(f"  {col} TEXT")
            if i < len(gdf.columns) - 2:  # Only add a comma if it's not the last column
                sql_buffer.write(",\n")
            else:
                sql_buffer.write("\n")

    # Finalize CREATE TABLE statement
    sql_buffer.write(");\n")

    # Generate INSERT statements for each row
    for i, row in gdf.iterrows():
        geom_wkt = row['geometry'].wkt  # Convert geometry to WKT (Well-Known Text)
        values = [f"'{row[col]}'" if col != 'geometry' else f"ST_GeomFromText('{geom_wkt}', 4326)" for col in gdf.columns]
        sql_buffer.write(f"INSERT INTO {table_name} ({', '.join(gdf.columns)}) VALUES ({', '.join(values)});\n")

    # Save SQL to a file
    with open(sql_file_path, 'w') as sql_file:
        sql_file.write(sql_buffer.getvalue())
    print(f"SQL file saved to: {sql_file_path}")

# Path to your shapefile (.shp file)
shapefile_path = '/Users/pingboyang/Desktop/researchapp.nosync/10_06shapefile/10_06shape.shp'

# Load the shapefile into a GeoDataFrame
gdf = gpd.read_file(shapefile_path)

# Output SQL file path
output_sql_file = '/Users/pingboyang/Desktop/researchapp.nosync/10_06shapefile/10_06shape.sql'

# Convert and save the GeoDataFrame to SQL file
gdf_to_sql_file(gdf, 'collisiondataline', output_sql_file)
