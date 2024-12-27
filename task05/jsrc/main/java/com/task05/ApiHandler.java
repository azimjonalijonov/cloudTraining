package com.task05;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		aliasName = "learn",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class ApiHandler implements RequestHandler<ApiGatewayEvent, Map<String, Object>> {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Map<String, Object> handleRequest(ApiGatewayEvent request, Context context) {
		// Generate unique ID and timestamp
		String id = UUID.randomUUID().toString();
		String createdAt = LocalDateTime.now().toString();

		// Extract request details
		int principalId = request.getPrincipalId();
		Map<String, String> content = request.getContent();
		String contentAsJSON = convertToJSON(content);

		context.getLogger().log("Processing request: " + request);

		// Add item to DynamoDB
		boolean success = addItem(id, principalId, createdAt, contentAsJSON);

		if (!success) {
			// Return 500 response in case of DynamoDB error
			return createErrorResponse("Failed to process the request.");
		}

		// Construct and return success response
		return createSuccessResponse(id, principalId, createdAt, content);
	}

	private boolean addItem(String id, int principalId, String createdAt, String contentAsJSON) {
		final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
		DynamoDB dynamoDb = new DynamoDB(client);
		String tableName = "cmtr-580435c6-Events-test";

		Table table = dynamoDb.getTable(tableName);
		Item item = new Item()
				.withPrimaryKey("id", id)
				.withInt("principalId", principalId)
				.withString("createdAt", createdAt)
				.withJSON("body", contentAsJSON);

		try {
			PutItemOutcome putItemOutcome = table.putItem(item);
			System.out.println("PutItemOutcome: " + putItemOutcome);
			return true;
		} catch (ResourceNotFoundException e) {
			System.err.format("Error: Table \"%s\" not found.\n", tableName);
			return false;
		} catch (AmazonServiceException e) {
			System.err.println("AWS Service Exception: " + e.getMessage());
			return false;
		}
	}

	private String convertToJSON(Map<String, String> content) {
		try {
			return objectMapper.writeValueAsString(content);
		} catch (JsonProcessingException e) {
			System.err.println("Failed to convert content to JSON: " + e.getMessage());
			return "{}"; // Return empty JSON on failure
		}
	}

	private Map<String, Object> createSuccessResponse(String id, int principalId, String createdAt, Map<String, String> content) {
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("statusCode", 201);
		resultMap.put("event", Map.of(
				"id", id,
				"principalId", principalId,
				"createdAt", createdAt,
				"body", content
		));
		return resultMap;
	}

	private Map<String, Object> createErrorResponse(String message) {
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("statusCode", 500);
		resultMap.put("error", message);
		return resultMap;
	}
}

class ApiGatewayEvent {
	private int principalId;
	private Map<String, String> content;

	public ApiGatewayEvent() {
	}

	public ApiGatewayEvent(int principalId, Map<String, String> content) {
		this.principalId = principalId;
		this.content = content;
	}

	public int getPrincipalId() {
		return principalId;
	}

	public void setPrincipalId(int principalId) {
		this.principalId = principalId;
	}

	public Map<String, String> getContent() {
		return content;
	}

	public void setContent(Map<String, String> content) {
		this.content = content;
	}

	@Override
	public String toString() {
		return "ApiGatewayEvent{" +
				"principalId=" + principalId +
				", content=" + content +
				'}';
	}
}
