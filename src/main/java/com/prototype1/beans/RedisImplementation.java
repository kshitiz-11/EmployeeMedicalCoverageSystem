package com.prototype1.beans;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


import com.google.gson.JsonObject;
import com.prototype1.services.KafkaPublisher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.prototype1.constants.Constant;

import io.lettuce.core.RedisException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class RedisImplementation {

	Map<String,String> relationMap = new HashMap<String,String>();
	
	private static JedisPool jendisPool = null;

	@Autowired
	private KafkaPublisher kafkaPublisher;
	
	@Autowired
	private EtagManager etagManager;
		
	public RedisImplementation() {
		jendisPool = new JedisPool(Constant.redisHost, Constant.redisPort);
	}
	
	public String insertObject(JSONObject jsonObject) {
			String planId = jsonObject.getString("objectId");
			//kafkaPublisher.publish(jsonObject.toString(), "index");
			JSONObject obj1 = new JSONObject(jsonObject.toString());
			indexEachObject(obj1, planId);

			String uniqueId = jsonObject.getString("objectType") + Constant.SEPERATOR + jsonObject.getString("objectId");
			if(insertEachObject(jsonObject, uniqueId))
				return jsonObject.getString("objectId");
			else
				return null;
	}
	
	public boolean insertEachObject(JSONObject jsonObject, String uuid) {
		try {
			Jedis jedis = jendisPool.getResource();
			Map<String,String> simpleMap = new HashMap<String,String>();
			
			for(Object key : jsonObject.keySet()) {
				String attributeKey = String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));


				String edge = attributeKey;
				if(attributeVal instanceof JSONObject) {
					JSONObject embdObject = (JSONObject) attributeVal;

					//ignore
					JSONObject json = new JSONObject(); json. put(attributeKey, embdObject);
					json.put("parent_id", uuid);


					String setKey = uuid + Constant.SEPERATOR + edge;
					String embd_uuid = embdObject.get("objectType") + Constant.SEPERATOR + embdObject.getString("objectId");
					jedis.sadd(setKey, embd_uuid);
					insertEachObject(embdObject, embd_uuid);
				} else if (attributeVal instanceof JSONArray) {

					JSONArray jsonArray = (JSONArray) attributeVal;
					Iterator<Object> jsonIterator = jsonArray.iterator();
					String setKey = uuid + Constant.SEPERATOR + edge;
					while(jsonIterator.hasNext()) {
						JSONObject embdObject = (JSONObject) jsonIterator.next();
						String embd_uuid = embdObject.get("objectType") + Constant.SEPERATOR + embdObject.getString("objectId");
						jedis.sadd(setKey, embd_uuid);
						insertEachObject(embdObject, embd_uuid);
					}
				} else {
					simpleMap.put(attributeKey, String.valueOf(attributeVal));
				}
			}
			jedis.hmset(uuid, simpleMap);
			jedis.close();
		}
		catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}




	public boolean indexEachObject(JSONObject jsonObject, String uuid) {
		try {

			Map<String,String> simpleMap = new HashMap<String,String>();


			for(Object key : jsonObject.keySet()) {
				String attributeKey = String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));


				String edge = attributeKey;
				if(attributeVal instanceof JSONObject) {
					JSONObject embdObject = (JSONObject) attributeVal;



					JSONObject joinObj = new JSONObject();
					if(edge.equals("planserviceCostShares") && embdObject.getString("objectType").equals("membercostshare")){
						joinObj.put("name", "planservice_membercostshare");

					}
					else
					joinObj.put("name", embdObject.getString("objectType"));
					joinObj.put("parent", uuid);
					embdObject.put("plan_service", joinObj );

					JSONObject json = new JSONObject(); json.put("node", embdObject);
					json.put("parent_id", uuid);

					kafkaPublisher.publish(json.toString(), "index");

					System.out.println(json);

					String embd_uuid = embdObject.getString("objectId");


				} else if (attributeVal instanceof JSONArray) {

					JSONArray jsonArray = (JSONArray) attributeVal;
					Iterator<Object> jsonIterator = jsonArray.iterator();

					while(jsonIterator.hasNext()) {
						JSONObject embdObject = (JSONObject) jsonIterator.next();
						JSONObject json = new JSONObject(); json.put("node", embdObject);
						json.put("parent_id", uuid);
						//kafkaPublisher.publish(json.toString(), "index");


						System.out.println("embobj is : " + json);
						String embd_uuid = embdObject.getString("objectId");
						relationMap.put(embd_uuid, uuid);

						indexEachObject(embdObject, embd_uuid);
					}
				} else {
					simpleMap.put(attributeKey, String.valueOf(attributeVal));
				}
			}
			System.out.println(simpleMap);

			JSONObject joinObj = new JSONObject();
			joinObj.put("name", simpleMap.get("objectType"));

			if(!simpleMap.containsKey("planType"))
			joinObj.put("parent", relationMap.get(uuid));
			//simpleMap.put("plan_service", joinObj.toString() );

			JSONObject obj1 = new JSONObject(simpleMap);
			obj1.put("plan_service", joinObj);

			JSONObject obj = new JSONObject();

			obj.put("node", obj1);
			System.out.println(relationMap);
			obj.put("parent_id", relationMap.get(uuid));

			kafkaPublisher.publish(obj.toString(), "index");




		}
		catch(JedisException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}


	public JSONObject getObject(String key) {
		JSONObject jsonObject = getEachObject("plan" + Constant.SEPERATOR + key);
		if(jsonObject != null)
			return jsonObject;
		else
			return null;
	}
	
	public JSONObject getEachObject(String uuid) {
		try {
			Jedis jedis = jendisPool.getResource();
			JSONObject jsonObject = new JSONObject();
			System.out.println("Reading keys from pattern");
			Set<String> keys = jedis.keys(uuid+Constant.SEPERATOR+"*");
			for(String key : keys) {
				Set<String> jsonKeySet = jedis.smembers(key);
				if(jsonKeySet.size() > 1) {
					
					JSONArray jsonArray = new JSONArray();
					Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
					while(jsonKeySetIterator.hasNext()) {
						jsonArray.put(getEachObject(jsonKeySetIterator.next()));
					}
					jsonObject.put(key.substring(key.lastIndexOf(Constant.SEPERATOR)+1), jsonArray);
				} else {
								  //key.substring(key.lastIndexOf(SEP) + 4), ja
					Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
					JSONObject embdObject = null;
					while(jsonKeySetIterator.hasNext()) {
						embdObject = getEachObject(jsonKeySetIterator.next());
					}
					jsonObject.put(key.substring(key.lastIndexOf(Constant.SEPERATOR)+1), embdObject);
					
				}
				
			}
			Map<String,String> simpleMap = jedis.hgetAll(uuid);
			for(String simpleKey : simpleMap.keySet()) {
				jsonObject.put(simpleKey, simpleMap.get(simpleKey));
			}
			
			jedis.close();
			return jsonObject;
		} catch(RedisException e) {
			e.printStackTrace();
            return null;
		}
	}
	
	public String patchObject(JSONObject jsonObject, String planId) {
		if (patchEachObject(jsonObject)) {
			JSONObject completeJsonObject = getObject(planId);
			try {

				String newETag = etagManager.getETag(completeJsonObject);
				return newETag;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public boolean patchEachObject(JSONObject jsonObject) {
		try {
			Jedis jedis = jendisPool.getResource();
			String uuid = jsonObject.getString("objectType") + Constant.SEPERATOR + jsonObject.getString("objectId");
			Map<String,String> simpleMap = jedis.hgetAll(uuid);
			if(simpleMap.isEmpty()) {
				simpleMap = new HashMap<String,String>();
			}
						
			for(Object key : jsonObject.keySet()) {
				String attributeKey = String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));
				String edge = attributeKey;
				
				if(attributeVal instanceof JSONObject) {
					
					JSONObject embdObject = (JSONObject) attributeVal;
					String setKey = uuid + Constant.SEPERATOR + edge;
					String embd_uuid = embdObject.get("objectType") + Constant.SEPERATOR + embdObject.getString("objectId");
					jedis.sadd(setKey, embd_uuid);
					patchEachObject(embdObject);
					
				} else if (attributeVal instanceof JSONArray) {
					
					JSONArray jsonArray = (JSONArray) attributeVal;
					Iterator<Object> jsonIterator = jsonArray.iterator();
					String setKey = uuid + Constant.SEPERATOR + edge;
					
					while(jsonIterator.hasNext()) {
						JSONObject embdObject = (JSONObject) jsonIterator.next();
						String embd_uuid = embdObject.get("objectType") + Constant.SEPERATOR + embdObject.getString("objectId");
						jedis.sadd(setKey, embd_uuid);
						patchEachObject(embdObject);
					}
					
				} else {
					simpleMap.put(attributeKey, String.valueOf(attributeVal));
				}
			}

			System.out.println("simple map is -----" + simpleMap );
			jedis.hmset(uuid, simpleMap);
			jedis.close();
			return true;
			
		} catch(JedisException e) {
			e.printStackTrace();
			return false;
		}
	}
	
public boolean put(JSONObject jsonObject) {
		try {
			return insertObject(jsonObject)!=null?true:false;
		}catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
public boolean deleteObject(String body) {
//		JSONObject json = new JSONObject(body);
//		if (!json.has("objectType") || !json.has("objectId"))
//			return false;

		return deleteUtil("plan" + Constant.SEPERATOR + body);
}

public boolean deleteUtil(String uuid) {
	try {
		Jedis jedis = jendisPool.getResource();
		Set<String> keys = jedis.keys(uuid+Constant.SEPERATOR+"*");
		for(String key : keys) {
			Set<String> jsonKeySet = jedis.smembers(key);
			for(String embd_uuid : jsonKeySet) {
				deleteUtil(embd_uuid);
			}
			jedis.del(key);
		}
		jedis.del(uuid);
		jedis.close();
		return true;
	} catch(JedisException e) {
		e.printStackTrace();
		return false;
	}
}

}
