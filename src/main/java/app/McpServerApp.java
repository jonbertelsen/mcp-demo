package app;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class McpServerApp {
    private static final String SERVER_NAME = "demo-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String ADD_NUMBERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "a": { "type": "integer" },
                "b": { "type": "integer" }
              },
              "required": ["a", "b"]
            }
            """;

    private static final String CURRENT_TIME_SCHEMA = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;

    public static void main(String[] args) {
        var jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);
        var server = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .toolCall(addNumbersTool(jsonMapper), (exchange, request) -> {
                    int a = (int) request.arguments().get("a");
                    int b = (int) request.arguments().get("b");
                    return textResult("Result: " + (a + b));
                })
                .toolCall(currentTimeTool(jsonMapper), (exchange, request) ->
                        textResult(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        awaitShutdown(server::close);
    }

    private static Tool addNumbersTool(JacksonMcpJsonMapper jsonMapper) {
        return Tool.builder()
                .name("add_numbers")
                .description("Add two numbers")
                .inputSchema(jsonMapper, ADD_NUMBERS_SCHEMA)
                .build();
    }

    private static Tool currentTimeTool(JacksonMcpJsonMapper jsonMapper) {
        return Tool.builder()
                .name("current_time")
                .description("Return the current server time in ISO-8601 format")
                .inputSchema(jsonMapper, CURRENT_TIME_SCHEMA)
                .build();
    }

    private static CallToolResult textResult(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .build();
    }

    private static void awaitShutdown(Runnable closeServer) {
        try {
            new CountDownLatch(1).await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeServer.run();
        }
    }
}
