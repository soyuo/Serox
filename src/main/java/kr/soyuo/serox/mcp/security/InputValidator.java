package kr.soyuo.serox.mcp.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates tool arguments against a small JSON-Schema subset: object with typed properties,
 * {@code required}, string length, integer range, and no unknown properties.
 */
public final class InputValidator {
  /** Prevents instantiation of this stateless utility. */
  private InputValidator() {}

  /** Returns the problems found, or an empty list when the arguments satisfy the schema. */
  public static List<String> validate(JsonObject schema, JsonObject arguments) {
    List<String> problems = new ArrayList<>();
    JsonObject properties =
        schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
    for (Map.Entry<String, JsonElement> entry : arguments.entrySet()) {
      if (!properties.has(entry.getKey())) {
        problems.add("Unknown argument: " + safeName(entry.getKey()));
      }
    }
    if (schema.has("required")) {
      schema
          .getAsJsonArray("required")
          .forEach(
              name -> {
                if (!arguments.has(name.getAsString()) || arguments.get(name.getAsString()).isJsonNull()) {
                  problems.add("Missing argument: " + name.getAsString());
                }
              });
    }
    for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
      if (arguments.has(property.getKey())) {
        check(
            property.getKey(),
            property.getValue().getAsJsonObject(),
            arguments.get(property.getKey()),
            problems);
      }
    }
    return problems;
  }

  private static void check(
      String name, JsonObject rule, JsonElement value, List<String> problems) {
    String type = rule.has("type") ? rule.get("type").getAsString() : "string";
    switch (type) {
      case "string" -> {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
          problems.add(name + " must be a string.");
          return;
        }
        String text = value.getAsString();
        if (rule.has("minLength") && text.strip().length() < rule.get("minLength").getAsInt()) {
          problems.add(name + " is too short.");
        }
        if (rule.has("maxLength") && text.length() > rule.get("maxLength").getAsInt()) {
          problems.add(name + " is too long.");
        }
      }
      case "integer" -> {
        if (!value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isNumber()
            || value.getAsDouble() != Math.rint(value.getAsDouble())) {
          problems.add(name + " must be an integer.");
          return;
        }
        long number = value.getAsLong();
        if (rule.has("minimum") && number < rule.get("minimum").getAsLong()) {
          problems.add(name + " is too small.");
        }
        if (rule.has("maximum") && number > rule.get("maximum").getAsLong()) {
          problems.add(name + " is too large.");
        }
      }
      default -> problems.add(name + " has an unsupported type.");
    }
  }

  private static String safeName(String name) {
    String shortened = name.length() > 40 ? name.substring(0, 40) : name;
    return shortened.replaceAll("[^A-Za-z0-9_.-]", "?");
  }
}
