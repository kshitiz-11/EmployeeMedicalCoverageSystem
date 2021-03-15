package com.prototype1.controller;

import com.prototype1.services.TokenService;
import org.springframework.web.bind.annotation.RestController;

import com.prototype1.beans.EtagManager;
import com.prototype1.beans.JsonValidator;
import com.prototype1.beans.RedisImplementation;
import com.prototype1.services.KafkaPublisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.json.JSONObject;

@RestController
@RequestMapping("/")
public class MainController {

	@Autowired
	private JsonValidator jsonValidator;
	@Autowired
	private RedisImplementation redisImplementation;
	@Autowired
	private EtagManager etagManager;
	
	@Autowired
	private KafkaPublisher kafkaPublisher;

	@Autowired
	private TokenService tokenService;

	private String key = "n2r5u8x/A?D(G+Kb";
	private String algorithm = "AES";

	Map<String, Object> map = new HashMap<String, Object>();

	@RequestMapping("")
	public String index() throws IOException {
		return "Hello World";
	}

	@RequestMapping(value = "/plan", method = RequestMethod.POST)
	public ResponseEntity<String> savePlan(HttpEntity<String> input, @RequestHeader HttpHeaders requestHeaders) throws Exception {

		map.clear();
		if (!tokenService.validateToken(requestHeaders.getFirst("Authorization"))) {
			return new ResponseEntity<String>("Unable to validate Token", HttpStatus.NOT_ACCEPTABLE);
		}

		if (input.getBody() == null)
			return new ResponseEntity<String>("Null Body Passed", HttpStatus.BAD_REQUEST);

		JSONObject jsonObject = new JSONObject(input.getBody());
		//String uniqueId1 = redisImplementation.insertObject(jsonObject);
		String validated = jsonValidator.validateJSONBySchema(jsonObject);
		if (validated != null) {
			return new ResponseEntity<String>("Validation Failed with JSON Schema, Reason : " + validated,
					HttpStatus.BAD_REQUEST);
		} else {
			String uniqueId = redisImplementation.insertObject(jsonObject);
			if (uniqueId != null) {
				String etag=etagManager.getETag(jsonObject);
				HttpHeaders responseHeader=new HttpHeaders();
				System.out.println(etag);
				responseHeader.setETag(etag);
				String x = input.getBody();

				return new ResponseEntity<String>("Object Saved, Object ID : " + uniqueId, responseHeader, HttpStatus.CREATED);
			}	
			return new ResponseEntity<String>("Oops something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
		}

	}

	@RequestMapping(value = "/plan/{key}", method = RequestMethod.GET)
	public ResponseEntity<String> getPlan(@PathVariable String key, HttpEntity<String> input,
			@RequestHeader HttpHeaders requestHeaders) throws Exception {
		map.clear();
		if (!tokenService.validateToken(requestHeaders.getFirst("Authorization"))) {
			return new ResponseEntity<String>("Unable to validate Token", HttpStatus.NOT_ACCEPTABLE);
		}

		if (key == null)
			return new ResponseEntity<String>("Null Key Passed", HttpStatus.BAD_REQUEST);

		JSONObject result = redisImplementation.getObject(key);

		HttpHeaders headers = new HttpHeaders();
		if (result != null) {
			String etag = etagManager.getETag(result);
			if (!etagManager.verifyETag(result, requestHeaders.getIfNoneMatch())) {
				headers.setETag(etag);
				return new ResponseEntity<String>(result.toString(4),headers, HttpStatus.OK);
			} else {
				headers.setETag(etag);
				return new ResponseEntity<String>(map.toString(), headers, HttpStatus.NOT_MODIFIED);
			}
		}
		
		return new ResponseEntity<String>("No data found for the key " + key, HttpStatus.NOT_FOUND);

	}

	@RequestMapping(value = "/plan/{key}", method = RequestMethod.DELETE,produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> deletePlan(@PathVariable String key, @RequestBody(required = true) String body,
			@RequestHeader HttpHeaders requestHeaders) throws Exception {
		map.clear();
		if (!tokenService.validateToken(requestHeaders.getFirst("Authorization"))) {
			return new ResponseEntity<String>("Unable to validate Token", HttpStatus.NOT_ACCEPTABLE);
		}

		if (key == null)
			return new ResponseEntity<String>("Null Key Passed", HttpStatus.BAD_REQUEST);

		boolean result = redisImplementation.deleteObject(key);
		if (result == true) {
			JSONObject json = new JSONObject(body);
			if (json.has("objectType") || json.has("objectId")) {
				//kafkaPublisher.publish(json.getString("objectId"), "delete");
			}
			return new ResponseEntity<String>("Object Deleted for Key : " + key, HttpStatus.OK);
		}

		return new ResponseEntity<String>("No data found for the key " + key, HttpStatus.NOT_FOUND);

	}

	@RequestMapping(value = "/token", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> generateToken() {
		map.clear();
		JSONObject jsonToken = new JSONObject();
		jsonToken.put("Issuer", "Kshitiz");

		TimeZone timeZone = TimeZone.getTimeZone("UTC");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		dateFormat.setTimeZone(timeZone);

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.MINUTE, 1);
		Date date = calendar.getTime();

		jsonToken.put("expiry", dateFormat.format(date));
		String token = jsonToken.toString();
		SecretKey secretKey = loadKey();

		try {
			Cipher cipher = Cipher.getInstance(algorithm);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			byte[] encrBytes = cipher.doFinal(token.getBytes());
			String encoded = Base64.getEncoder().encodeToString(encrBytes);
			map.put("token", encoded);
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.ACCEPTED);

		} catch (Exception e) {
			e.printStackTrace();
			map.put("message", "Unable to generate Token");
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.UNAUTHORIZED);
		}

	}

	@RequestMapping(method = RequestMethod.PATCH, value = "/plan/{planID}")
	public ResponseEntity<Map<String, Object>> update(@PathVariable(name = "planID", required = true) String planID,
			@RequestBody(required = true) String body, @RequestHeader HttpHeaders requestHeaders) throws Exception {
		map.clear();
		if (!tokenService.validateToken(requestHeaders.getFirst("Authorization"))) {
			map.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.UNAUTHORIZED);
		}

		JSONObject jsonObject = new JSONObject(body);
		HttpHeaders responseHeaders = new HttpHeaders();

		JSONObject planJSON = redisImplementation.getObject(planID);
		if (planJSON != null) {
			String etag = etagManager.getETag(planJSON);
			if (etagManager.verifyETag(planJSON, requestHeaders.getIfMatch())) {
				String newETag = redisImplementation.patchObject(jsonObject, planID);
				if (newETag == null) {
					map.put("message", "Unable to Update");
					return new ResponseEntity<Map<String, Object>>(map, HttpStatus.BAD_REQUEST);
				}
				responseHeaders.setETag(newETag);
				map.put("message", "Successfully Updated");

				JSONObject result = redisImplementation.getObject(planID);

				//kafkaPublisher.publish(planID, "delete");

				//kafkaPublisher.publish(result.toString(), "index");
				return new ResponseEntity<Map<String, Object>>(map, responseHeaders, HttpStatus.OK);
			} else {
				if (requestHeaders.getIfMatch().isEmpty()) {
					map.put("message", "If-Match ETag required");
					return new ResponseEntity<Map<String, Object>>(map, responseHeaders,
							HttpStatus.PRECONDITION_REQUIRED);
				} else {
					responseHeaders.setETag(etag);
					return new ResponseEntity<Map<String, Object>>(map, responseHeaders,
							HttpStatus.PRECONDITION_FAILED);
				}
			}
		} else {
			map.put("message", "Invalid Plan Id");
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(method = RequestMethod.PUT, value = "/plan/{planID}",produces = MediaType.APPLICATION_JSON_VALUE, consumes = "application/json")
	public ResponseEntity<Map<String, Object>> put(@PathVariable(name = "planID", required = true) String planID,
			@RequestBody(required = true) String body, @RequestHeader HttpHeaders requestHeaders) throws Exception {
		map.clear();
		if (!tokenService.validateToken(requestHeaders.getFirst("Authorization"))) {
			map.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.UNAUTHORIZED);
		}

		JSONObject jsonObject = new JSONObject(body);
		String validated = jsonValidator.validateJSONBySchema(jsonObject);
		if (validated != null) {
			map.put("message", "Validation Failed with JSON Schema, Reason : " + validated);
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.BAD_REQUEST);
		}
		
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		
		JSONObject planJSON = redisImplementation.getObject(planID);
		if (planJSON != null) {
			String etag = etagManager.getETag(planJSON);
			if (etagManager.verifyETag(planJSON, requestHeaders.getIfMatch())) {

				if (!redisImplementation.put(jsonObject)) {
					map.put("message", "Unable to Update");
					return new ResponseEntity<Map<String, Object>>(map, HttpStatus.BAD_REQUEST);
				}
				String newETag = etagManager.getETag(redisImplementation.getObject(planID));
				responseHeaders.setETag(newETag);
				map.put("message", "Successfully updated");
				return new ResponseEntity<Map<String, Object>>(map, responseHeaders, HttpStatus.OK);
			} else {
				if (requestHeaders.getIfMatch().isEmpty()) {
					map.put("message", "If-Match ETag required");
					return new ResponseEntity<Map<String, Object>>(map, responseHeaders,
							HttpStatus.PRECONDITION_REQUIRED);
				} else {
					responseHeaders.setETag(etag);
					return new ResponseEntity<Map<String, Object>>(map, responseHeaders,
							HttpStatus.PRECONDITION_FAILED);
				}
			}
		} else {
			map.put("message", "Invalid Plan Id");
			return new ResponseEntity<Map<String, Object>>(map, HttpStatus.BAD_REQUEST);
		}
	}

	public boolean validateToken(HttpHeaders headers) {
		if (headers.getFirst("Authorization") == null)
			return false;

		String token = headers.getFirst("Authorization").substring(7);
		try {
			byte[] decodedToken = Base64.getDecoder().decode(token);
			SecretKey secretKey = loadKey();
			Cipher cipher = Cipher.getInstance(algorithm);
			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			String tokenString = new String(cipher.doFinal(decodedToken));
			JSONObject jsonToken = new JSONObject(tokenString);

			String expiryString = jsonToken.get("expiry").toString();
			Date currentDate = Calendar.getInstance().getTime();

			TimeZone timeZone = TimeZone.getTimeZone("UTC");
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
			formatter.setTimeZone(timeZone);

			Date expiry = formatter.parse(expiryString);
			currentDate = formatter.parse(formatter.format(currentDate));

			if (currentDate.after(expiry)) {
				return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private SecretKey loadKey() {
		return new SecretKeySpec(key.getBytes(), algorithm);
	}

}
