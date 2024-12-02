import psycopg2
import json

# Establish a connection to the PostgreSQL database
conn = psycopg2.connect(
    host="postgres-1.ctfojr4mfu0j.us-east-1.rds.amazonaws.com",
    database="postgres",
    user="postgres",
    password="bmcccis##"
)

cur = conn.cursor()

# Create the table in the database if it doesn't already exist
create_table_query = """
    CREATE TABLE IF NOT EXISTS collisiondata1021 (
        id SERIAL PRIMARY KEY,
        street VARCHAR(255),
        feature_type VARCHAR(10),
        trafdir VARCHAR(10),
        street_code VARCHAR(20),
        lzip INT,
        lboro INT,
        lcb2020 VARCHAR(10),
        rw_type VARCHAR(10),
        status INT,
        streetwidth_min FLOAT,
        streetwidth_max FLOAT,
        bikelane VARCHAR(10),
        posted_speed INT,
        snow_priority VARCHAR(10),
        gridcode INT,
        shape_length FLOAT,
        geom geometry(LineString, 4326)  -- Using WGS84 coordinate system
    );
"""
cur.execute(create_table_query)
conn.commit()

# Load your GeoJSON file
with open('/Users/pingboyang/Desktop/researchapp.nosync/10_06shapefile/nyc_map1021_js.geojson', 'r') as f:
    data = json.load(f)

# Define the batch size
batch_size = 500  # Insert 500 rows at a time

# Helper function to clean the input
def clean_value(value, expected_type):
    if value in [None, '', ' ', '  ']:
        # For integer fields, return 0 if empty or null
        if expected_type == int:
            return 0
        # For float fields, return 0.0 if empty or null
        elif expected_type == float:
            return 0.0
        # For string fields (like BikeLane), return '0' or default value if empty
        elif expected_type == str:
            return '0'
    try:
        if expected_type == int:
            return int(value)
        elif expected_type == float:
            return float(value)
    except ValueError:
        return None  # If conversion fails, return None
    return value

# Prepare the insert query template for PostgreSQL
insert_query = """
    INSERT INTO collisiondata1021 (
        street, feature_type, trafdir, street_code, lzip, lboro, 
        lcb2020, rw_type, status, streetwidth_min, 
        streetwidth_max, bikelane, 
        posted_speed, snow_priority, gridcode, 
        shape_length, geom
    ) VALUES (
        %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 
        %s, ST_GeomFromText(%s, 4326)
    );
"""

# Collect rows to insert
rows_to_insert = []

for feature in data['features']:
    properties = feature['properties']
    coordinates = feature['geometry']['coordinates']
    
    # Create the LineString from the coordinates
    linestring = 'LINESTRING (' + ', '.join([f"{lon} {lat}" for lon, lat in coordinates]) + ')'
    
    # Prepare the row data matching the updated table structure for database
    row = (
        properties.get('Street'),                                       # street
        clean_value(properties.get('FeatureTyp'), str),                 # feature_type (should be string)
        clean_value(properties.get('TrafDir'), str),                    # trafdir (string)
        properties.get('StreetCode'),                                   # street_code (string)
        clean_value(properties.get('LZip'), int),                       # lzip
        clean_value(properties.get('LBoro'), int),                      # lboro
        properties.get('LCB2020'),                                      # lcb2020 (as string)
        clean_value(properties.get('RW_TYPE'), str),                    # rw_type (string)
        clean_value(properties.get('Status'), int),                     # status
        clean_value(properties.get('StreetWidth_Min'), float),          # streetwidth_min
        clean_value(properties.get('StreetWidth_Max'), float),          # streetwidth_max
        clean_value(properties.get('BikeLane'), str),                   # bikelane (convert to '0' if empty)
        clean_value(properties.get('POSTED_SPEED'), int),               # posted_speed
        clean_value(properties.get('Snow_Priority'), str),              # snow_priority (string like 'S')
        clean_value(properties.get('gridcode'), int),                   # gridcode (if null, convert to 0)
        clean_value(properties.get('SHAPE_Length'), float),             # shape_length
        linestring                                                     # The geometry as the last element (for the ST_GeomFromText)
    )
    
    rows_to_insert.append(row)

    # Check if the batch size is reached and insert rows
    if len(rows_to_insert) >= batch_size:
        try:
            cur.executemany(insert_query, rows_to_insert)
            conn.commit()  # Commit the batch
            rows_to_insert = []  # Reset the list
        except psycopg2.Error as e:
            print(f"Error while inserting batch: {e}")
            conn.rollback()  # Rollback in case of error

# Insert any remaining rows if the batch size is not reached
if rows_to_insert:
    try:
        cur.executemany(insert_query, rows_to_insert)
        conn.commit()  # Commit the remaining rows
    except psycopg2.Error as e:
        print(f"Error while inserting final batch: {e}")
        conn.rollback()

# Close the cursor and connection
cur.close()
conn.close()

print("Data successfully inserted into the database!")
