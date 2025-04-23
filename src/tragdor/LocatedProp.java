package tragdor;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.json.JSONObject;

import codeprober.protocol.BinaryInputStream;
import codeprober.protocol.BinaryOutputStream;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;

public class LocatedProp {

	public final NodeLocator locator;
	public final Property prop;

	private int hashCode;
	// TODO consider replacing the *Str with encoded data-stream representations
	// instead. writeTo is more well defined for comparison purposes
//	private String locatorStr;
//	private String propStr;
	private byte[] identityArr;

	public LocatedProp(NodeLocator locator, Property prop) {
		this.locator = locator;
		this.prop = prop;
	}

	public LocatedProp(BinaryInputStream src) throws IOException {
		this.locator = new NodeLocator(src);
		this.prop = new Property(src);
	}

	public LocatedProp(DataInputStream src) throws IOException {
		this.locator = new NodeLocator(src);
		this.prop = new Property(src);
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			prepareForEquals();
//			hashCode = Objects.hash(locatorStr, propStr);
			hashCode = Arrays.hashCode(identityArr);
//			hashCode = Objects.hash(locator.toJSON().toString(), prop.toJSON().toString());
		}
		return hashCode;
	}

	private void prepareForEquals() {
		if (identityArr == null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
//			final StringPoolingDataEncoder dos = new StringPoolingDataEncoder();
			try {
				locator.writeTo(dos);
				prop.writeTo(dos);
//				identityArr = dos.getResult();
			} catch (IOException impossible) {
				System.out.println("Impossible exception happened");
				impossible.printStackTrace();
				System.exit(1);
			}
			identityArr = baos.toByteArray();
//			locatorStr = JSONStringifier.jsonToString(locator.toJSON());
//			propStr = JSONStringifier.jsonToString(prop.toJSON());
		}
	}

	public void writeTo(BinaryOutputStream dst) throws IOException {
		locator.writeTo(dst);
		prop.writeTo(dst);
	}

	public void writeTo(DataOutputStream dst) throws IOException {
		locator.writeTo(dst);
		prop.writeTo(dst);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final LocatedProp other = (LocatedProp) obj;
		prepareForEquals();
		other.prepareForEquals();

//		return locatorStr.equals(other.locatorStr) && propStr.equals(other.propStr);
		return Arrays.equals(identityArr, other.identityArr);
	}

	public JSONObject toJSON() {
		return new JSONObject() //
				.put("loc", locator.toJSON()) //
				.put("prop", prop.toJSON());
	}

	public static LocatedProp fromJSON(JSONObject obj) {
		return new LocatedProp( //
				NodeLocator.fromJSON(obj.getJSONObject("loc")), //
				Property.fromJSON(obj.getJSONObject("prop")) //
		);
	}

	@Override
	public String toString() {
		return toJSON().toString();
	}

//	public static String jsonToOrderedString(JSONObject obj) {
////		StringBuilder sb = new StringBuilder("{");
//		final List<String> keys = new ArrayList<>(obj.keySet());
//		final StringWriter w = new StringWriter();
//		w.append("{");
////		synchronized (w.getBuffer()) {
////		return write(w, indentFactor, 0).toString();
////		}
//
//		Collections.sort(keys);
//		for (String key : keys) {
////			sb.app
//		}
//		w.append("}");
//		return sb.toString();
//	}
}
