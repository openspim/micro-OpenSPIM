package spim.io;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

public class StorageOpener {
	public static StorageType checkStorageType(String dir, String prefix) {
		if (new File(dir + "/" + prefix + "_metadata.txt").exists()) {
				JsonObject data = readJSONMetadata(dir, "", prefix);

				if(data.has("Summary")) {
					JsonObject sum = data.get("Summary").getAsJsonObject();

					if(sum.has("StorageType")) {
						System.out.println(sum.get("StorageType"));

						return StorageType.valueOf(sum.get("StorageType").getAsString());
					}
				}
		}
		return null;
	}

	@SuppressWarnings("Duplicates")
	public static JsonObject readJSONMetadata(String dir, String pos, String prefix) {
		String fileStr;
		String path = new File(new File(dir, pos), prefix + "_metadata.txt").getPath();

		try {
			fileStr = TextUtils.readTextFile(path);
		}
		catch (IOException e) {
			ReportingUtils.logError(e, "Unable to read text file at " + path);
			return null;
		}

		JsonReader reader = new JsonReader(new StringReader(fileStr));
		reader.setLenient(true);
		JsonParser parser = new JsonParser();
		try {
			return parser.parse(reader).getAsJsonObject();
		}
		catch (JsonIOException e) {
			// Try again with an added curly brace because some old versions
			// failed to write the final '}' under some circumstances.
			try {
				reader = new JsonReader(new StringReader(fileStr + "}"));
				reader.setLenient(true);
				return parser.parse(reader).getAsJsonObject();
			}
			catch (JsonIOException e2) {
				// Give up.
				return null;
			} catch (JsonSyntaxException e2) {
				// Give up.
				return null;
			}
		} catch (JsonSyntaxException e) {
			// Try again with an added curly brace because some old versions
			// failed to write the final '}' under some circumstances.
			try {
				reader = new JsonReader(new StringReader(fileStr + "}"));
				reader.setLenient(true);
				return parser.parse(reader).getAsJsonObject();
			}
			catch (JsonIOException e2) {
				// Give up.
				return null;
			} catch (JsonSyntaxException e2) {
				// Give up.
				return null;
			}
		}
	}
}
