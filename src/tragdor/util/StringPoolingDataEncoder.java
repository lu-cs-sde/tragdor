package tragdor.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import codeprober.protocol.BinaryOutputStream.DataOutputStreamWrapper;

public class StringPoolingDataEncoder extends DataOutputStreamWrapper {

	private final ByteArrayOutputStream baos;
	private final Map<String, Integer> stringIds = new HashMap<>();
	private final List<String> stringPool = new ArrayList<>();

	public StringPoolingDataEncoder() {
		this(new ByteArrayOutputStream());
	}

	private StringPoolingDataEncoder(ByteArrayOutputStream baos) {
		super(new DataOutputStream(baos));
		this.baos = baos;

		// Reserve space for pool offset indicator
		try {
			writeInt(0);
		} catch (IOException impossible) {
			impossible.printStackTrace();
		}
	}

	public byte[] getResult() throws IOException {
		final int poolOffset = baos.size();

//		System.out.println("StringPool starts at " + poolOffset + ", contains " + stringPool.size() + " entries");
		writeInt(stringPool.size());
		for (String entry : stringPool) {
			super.writeUTF(entry);
		}

		final byte[] bytes = baos.toByteArray();
		bytes[0] = (byte) ((poolOffset >>> 24) & 0xFF);
		bytes[1] = (byte) ((poolOffset >>> 16) & 0xFF);
		bytes[2] = (byte) ((poolOffset >>> 8) & 0xFF);
		bytes[3] = (byte) ((poolOffset >>> 0) & 0xFF);
		return bytes;
	}

	@Override
	public void writeUTF(String v) throws IOException {
		Integer id = stringIds.get(v);
		if (id == null) {
			id = stringIds.size();
			stringIds.put(v, id);
			stringPool.add(v);
		}
		writeInt(id);
	}
}
