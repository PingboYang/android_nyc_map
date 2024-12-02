import requests
import psycopg2
from datetime import datetime, timedelta

# PostgreSQL connection details
DB_HOST = "postgres-1.ctfojr4mfu0j.us-east-1.rds.amazonaws.com"
DB_NAME = "mygisdb"
DB_USER = "postgres"
DB_PASS = "bmcccis##"

# NYC Open Data API endpoint and dataset ID
DATASET_ID = "h9gi-nx95"  # Motor Vehicle Collisions - Crashes
API_ENDPOINT = f"https://data.cityofnewyork.us/resource/{DATASET_ID}.json"

# Optional: Your App Token from NYC Open Data (if you have one)
APP_TOKEN = "SeVa9NrS8J0GiqywTrgbv5JjS"

# Function to fetch the latest two weeks of data
def fetch_latest_data():
    # Get the current date and date two weeks ago (without time)
    current_date = datetime.now().strftime("%Y-%m-%d")
    two_weeks_ago = (datetime.now() - timedelta(weeks=2)).strftime("%Y-%m-%d")
    
    # Define query parameters
    params = {
        "$where": f"crash_date between '{two_weeks_ago}' and '{current_date}'",
        "$limit": 50000,  # Adjust this limit if necessary
    }
    
    headers = {
        "X-App-Token": APP_TOKEN
    }
    
    # Fetch data from NYC Open Data
    response = requests.get(API_ENDPOINT, params=params, headers=headers)
    response.raise_for_status()  # Raise an error for bad responses
    
    return response.json(), two_weeks_ago, current_date

# Function to convert time to decimal (hours + minutes / 60)
def time_to_decimal(time_str):
    if time_str is None:
        return None
    try:
        hours, minutes = map(int, time_str.split(':'))
        return round(hours + minutes / 60, 2)
    except ValueError:
        return None

# Function to calculate the EPDO score based on the provided formula
def calculate_epdo(item):
    def safe_int(value):
        try:
            return int(value)
        except (TypeError, ValueError):
            return 0

    return (1 + 
            6 * safe_int(item.get('number_of_persons_injured', 0)) + 
            12 * safe_int(item.get('number_of_persons_killed', 0)) +
            6 * safe_int(item.get('number_of_pedestrians_injured', 0)) +
            12 * safe_int(item.get('number_of_pedestrians_killed', 0)) +
            6 * safe_int(item.get('number_of_cyclist_injured', 0)) +
            12 * safe_int(item.get('number_of_cyclist_killed', 0)) +
            6 * safe_int(item.get('number_of_motorist_injured', 0)) +
            12 * safe_int(item.get('number_of_motorist_killed', 0)))


# Function to calculate the time period based on time_decimal
def calculate_time_period(time_decimal):
    if time_decimal is None:
        return None
    # Define time periods from 0.0 to 23.99, dividing into 12 parts
    if 0 <= time_decimal < 2:
        return 1
    elif 2 <= time_decimal < 4:
        return 2
    elif 4 <= time_decimal < 6:
        return 3
    elif 6 <= time_decimal < 8:
        return 4
    elif 8 <= time_decimal < 10:
        return 5
    elif 10 <= time_decimal < 12:
        return 6
    elif 12 <= time_decimal < 14:
        return 7
    elif 14 <= time_decimal < 16:
        return 8
    elif 16 <= time_decimal < 18:
        return 9
    elif 18 <= time_decimal < 20:
        return 10
    elif 20 <= time_decimal < 22:
        return 11
    elif 22 <= time_decimal < 24:
        return 12
    return None  # In case the time_decimal is out of bounds (edge case)
# Function to delete rows where latitude is NULL
def delete_rows_with_null_latitude(table_name):
    conn = psycopg2.connect(
        host=DB_HOST,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASS
    )
    cur = conn.cursor()
    delete_query = f"DELETE FROM {table_name} WHERE latitude IS NULL;"
    cur.execute(delete_query)
    conn.commit()
    cur.close()
    conn.close()
    print(f"Deleted all rows where latitude is NULL in {table_name}.")

# Function to save data to PostgreSQL
def save_to_db(data, start_date, end_date):
    # Generate the table name using the start and end date (only the date part, no time)
    start_date_str = start_date.replace("-", "")
    end_date_str = end_date.replace("-", "")
    table_name = f"collisiondata_{start_date_str}_to_{end_date_str}"
    
    # Establish a database connection
    conn = psycopg2.connect(
        host=DB_HOST,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASS
    )
    cur = conn.cursor()
    
    # Create a table if it doesn't already exist
    create_table_query = f"""
        CREATE TABLE IF NOT EXISTS {table_name} (
            id SERIAL PRIMARY KEY,
            crash_date DATE,
            crash_time TIME,
            time_decimal NUMERIC(5, 2),   -- New column for time as decimal
            time_period INT,              -- New column for time period
            borough TEXT,
            zip_code TEXT,
            latitude NUMERIC,
            longitude NUMERIC,
            on_street_name TEXT,
            cross_street_name TEXT,
            number_of_persons_injured INT,
            number_of_persons_killed INT,
            number_of_pedestrians_injured INT,
            number_of_pedestrians_killed INT,
            number_of_cyclist_injured INT,
            number_of_cyclist_killed INT,
            number_of_motorist_injured INT,
            number_of_motorist_killed INT,
            epdo NUMERIC(10, 2)           -- New column for EPDO value
        );
    """
    cur.execute(create_table_query)
    
    
    # Define SQL insert query
    insert_query = f"""
        INSERT INTO {table_name} (
            crash_date, crash_time, time_decimal, time_period, borough, zip_code, latitude, longitude, 
            on_street_name, cross_street_name, number_of_persons_injured, number_of_persons_killed, 
            number_of_pedestrians_injured, number_of_pedestrians_killed, number_of_cyclist_injured, 
            number_of_cyclist_killed, number_of_motorist_injured, number_of_motorist_killed, epdo
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);
    """
    
    # Loop through the data and insert each record
    for item in data:
        time_decimal = time_to_decimal(item.get('crash_time'))
        epdo_value = calculate_epdo(item)
        time_period = calculate_time_period(time_decimal)
        
        cur.execute(insert_query, (
            item.get('crash_date'),
            item.get('crash_time'),
            time_decimal,  # Insert the calculated time_decimal value
            time_period,   # Insert the calculated time period value
            item.get('borough'),
            item.get('zip_code'),
            item.get('latitude'),
            item.get('longitude'),
            item.get('on_street_name'),
            item.get('cross_street_name'),
            item.get('number_of_persons_injured'),
            item.get('number_of_persons_killed'),
            item.get('number_of_pedestrians_injured'),
            item.get('number_of_pedestrians_killed'),
            item.get('number_of_cyclist_injured'),
            item.get('number_of_cyclist_killed'),
            item.get('number_of_motorist_injured'),
            item.get('number_of_motorist_killed'),
            epdo_value  # Insert the calculated EPDO value
        ))

    
    # Commit the transaction and close the connection
    conn.commit()
    # Delete rows where latitude is NULL before inserting new data
    delete_rows_with_null_latitude(table_name)
    cur.close()
    conn.close()

# Main function
def main():
    try:
        # Fetch the latest data
        print("Fetching latest data...")
        data, start_date, end_date = fetch_latest_data()
        
        # Save data to the database in a new table with the date range
        print(f"Saving {len(data)} records to the database...")
        save_to_db(data, start_date, end_date)
        
        print("Data saved successfully!")
    except Exception as e:
        print(f"Error: {e}")

# Schedule this script to run every 2 weeks (e.g., with a cron job)
if __name__ == "__main__":
    main()
