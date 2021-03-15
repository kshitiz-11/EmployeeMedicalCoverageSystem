package com.prototype1.beans;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

public class JsonValidator {
	
	public JsonValidator() {
		
	}
	
	public String validateJSONBySchema(JSONObject input) {
		try 
		{
			  File initialFile = new File("src/main/java/schema.json");
		      InputStream targetStream = new FileInputStream(initialFile);
			  JSONObject rawSchema = new JSONObject(new JSONTokener(targetStream));
			  Schema schema = SchemaLoader.load(rawSchema);
			  schema.validate(input); 
			  return null;
		}catch(Exception e) {
			System.out.println(e.toString());
			return e.getMessage();
		}		
	}
	
}
