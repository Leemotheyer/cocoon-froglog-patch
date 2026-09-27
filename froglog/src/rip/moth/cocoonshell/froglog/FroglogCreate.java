package rip.moth.cocoonshell.froglog;

import org.json.JSONObject;

/** What {@code POST /games} did. A 409 with {@code needs_confirmation} is a choice, not a failure. */
public final class FroglogCreate {
    private FroglogCreate() {}

    public static final class Outcome {
        public final long createdId;
        public final long existingId;
        public final String existingTitle;
        public final String error;

        public Outcome(long createdId, long existingId, String existingTitle, String error) {
            this.createdId = createdId;
            this.existingId = existingId;
            this.existingTitle = existingTitle == null ? "" : existingTitle;
            this.error = error;
        }

        public boolean created() {
            return error == null && createdId >= 0;
        }

        public boolean needsChoice() {
            return error == null && existingId >= 0;
        }
    }

    public static Outcome interpret(int code, String body) {
        String payload = body == null ? "" : body;
        if (code >= 200 && code < 300) {
            long id = -1;
            try {
                id = new JSONObject(payload).optLong("id", -1);
            } catch (Exception ignored) {
                id = -1;
            }
            if (id < 0) {
                return new Outcome(-1, -1, "", "Froglog did not return a game id");
            }
            return new Outcome(id, -1, "", null);
        }
        if (code == 409) {
            try {
                JSONObject json = new JSONObject(payload);
                if (json.optBoolean("needs_confirmation", false)) {
                    JSONObject existing = json.optJSONObject("existing_game");
                    if (existing != null && !existing.isNull("id")) {
                        long id = existing.optLong("id", -1);
                        if (id >= 0) {
                            return new Outcome(-1, id, existing.optString("title", ""), null);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fall through to the generic error.
            }
        }
        if (code == 429) {
            return new Outcome(-1, -1, "", "Froglog is limiting new games. Try again later.");
        }
        return new Outcome(-1, -1, "", message(payload, "Could not add the game (" + code + ")"));
    }

    private static String message(String body, String fallback) {
        try {
            JSONObject json = new JSONObject(body);
            String error = json.optString("error", "");
            if (!error.isEmpty()) {
                return error;
            }
            String message = json.optString("message", "");
            if (!message.isEmpty()) {
                return message;
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return fallback;
    }
}
