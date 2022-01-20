package it.unipi.smartPark;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class TelemetryDatabaseHandler {

	private static final String DB_URL ="jdbc:mysql://localhost:3306/smartPark"; 

	private static final String USER = "root";
	private static final String PASS = "root";
	private static final String[] TYPES_OF_DEVICES = {"park_lot", "smart_traffic_light", "flame_detector"};

	public static boolean init() {
		
		String createTableQuery = "CREATE TABLE IF NOT EXISTS %s_status (" + 
									"id INT UNSIGNED NOT NULL AUTO_INCREMENT, " +
									"timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " + 
									"%s_id VARCHAR(30) NOT NULL, " +
									"status VARCHAR(10) NOT NULL, " +
									"PRIMARY KEY(id))" + 
									"ENGINE = InnoDB;";
		
		
		try(Connection con = DriverManager.getConnection(DB_URL, USER, PASS);
				Statement statement = con.createStatement()){
			
			for(String device : TYPES_OF_DEVICES) {
				
				statement.executeUpdate(String.format(createTableQuery, device, device));
				System.out.println("[INFO - Telemetry Database Handler] : " + device + "_status table created!");
			}
			
			System.out.println();
			return true;
			
		} catch (SQLException ex) {
			
			System.out.println("[ERR  - Telemetry Database Handler] : Error while connecting to the database!");
			System.out.println();
			ex.getStackTrace();
			return false;
		}
	}
	
	public static void saveUpdate(String typeOfDevice, Timestamp timestamp, String deviceID, String status) {
		
		String saveReadingQuery = "INSERT INTO %s_status (timestamp, %s_id, status) " +
									"VALUES (?,?,?);"; 
		
		System.out.println("[INFO - Telemetry Database Handler] : Saving update for " + typeOfDevice + "!");
		
		try(Connection con = DriverManager.getConnection(DB_URL, USER, PASS);){
			
			PreparedStatement ps = con.prepareStatement(String.format(saveReadingQuery, typeOfDevice, typeOfDevice), Statement.RETURN_GENERATED_KEYS);
			ps.setTimestamp(1, timestamp);
			ps.setString(2, deviceID);
			ps.setString(3, status);
			
			int insertedRows = ps.executeUpdate();
			
			if(insertedRows != 0) {
				System.out.println("[INFO - Telemetry Database Handler] : Inserted row in "+ typeOfDevice +"_status table!");
				System.out.println();
				
				printInsertedRow(ps, typeOfDevice, timestamp, deviceID, status);
				
			}
			else {
				System.out.println("[ERR - Telemetry Database Handler] : No rows inserted in "+ typeOfDevice +"_status table!");
			}
			
		} catch (SQLException ex) {
			
			System.out.println("[ERR  - Telemetry Database Handler] : Error while saving reading!");
			System.out.println();
			ex.printStackTrace();
		} finally {
			
			System.out.println();
		}
		
	}

	private static void printInsertedRow(PreparedStatement ps, String typeOfDevice, Timestamp timestamp, String deviceID, String status) {
		
		try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
			
			// Print to video the inserted row
            if (generatedKeys.next()) {
            	String deviceIDColumnName = String.format("%s_id", typeOfDevice);
            	System.out.println("-------------------------------------------------------------------------------------------------------");
            	System.out.println("| ID: " + generatedKeys.getLong(1) + " | Timestamp: " + timestamp + 
            						" | " + deviceIDColumnName + ": " + deviceID + " | status: " + status + " |");
            	System.out.println("-------------------------------------------------------------------------------------------------------");
            }
            else {
                throw new SQLException("Creating user failed, no ID obtained.");
            }
        } catch (SQLException ex) {
        	System.out.println("[ERR  - Telemetry Database Handler] : Error while retrieving inserted data!");
			System.out.println();
			ex.printStackTrace();
        }
	}
	
}
