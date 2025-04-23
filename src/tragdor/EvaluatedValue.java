package tragdor;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.json.JSONObject;

import codeprober.protocol.BinaryInputStream;
import codeprober.protocol.BinaryOutputStream;
import codeprober.protocol.data.RpcBodyLine;

public class EvaluatedValue {

	public enum Kind {
		EXCEPTION, VALUE
	}

	public final Kind kind;
	public final RpcBodyLine value;

//	private String valueStr;
	private int hashCode;
	private byte[] identityArr;

	public EvaluatedValue(Kind kind, RpcBodyLine value) {
		this.kind = kind;
		this.value = value;
	}

	public EvaluatedValue(BinaryInputStream src) throws IOException {
		this.kind = src.readBoolean() ? Kind.VALUE : Kind.EXCEPTION;
		this.value = new RpcBodyLine(src);
	}

	public EvaluatedValue(DataInputStream src) throws IOException {
		this.kind = src.readBoolean() ? Kind.VALUE : Kind.EXCEPTION;
		this.value = new RpcBodyLine(src);
	}

	private byte[] getIdentityArr() {
		if (identityArr == null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			try {
				dos.writeByte(kind.ordinal());
				value.writeTo(dos);
			} catch (IOException impossible) {
				System.out.println("Impossible exception happened");
				impossible.printStackTrace();
				System.exit(1);
			}
			identityArr = baos.toByteArray();

//			return JSONStringifier.jsonToString(value.toJSON());
		}
		return identityArr;
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			hashCode = Arrays.hashCode(getIdentityArr());
		}
		return hashCode;
	}

	@Override
	public String toString() {
		return value.toJSON().toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final EvaluatedValue other = (EvaluatedValue) obj;
		return Arrays.equals(getIdentityArr(), other.getIdentityArr());
	}

	public static final EvaluatedValue dummy = new EvaluatedValue(Kind.EXCEPTION, RpcBodyLine.fromPlain("<dummy>"));

	public void writeTo(BinaryOutputStream dos) throws IOException {
		dos.writeBoolean(kind == Kind.VALUE);
		value.writeTo(dos);
		;
	}

	public void writeTo(DataOutputStream dos) throws IOException {
		dos.writeBoolean(kind == Kind.VALUE);
		value.writeTo(dos);
		;
	}

	public JSONObject toJSON() {
		return new JSONObject() //
				.put("k", kind == Kind.VALUE).put("v", value.toJSON());
	}

	public static EvaluatedValue fromJSON(JSONObject src) {
		return new EvaluatedValue( //
				src.getBoolean("k") ? Kind.VALUE : Kind.EXCEPTION, //
				RpcBodyLine.fromJSON(src.getJSONObject("v")) //
		);
	}
}
