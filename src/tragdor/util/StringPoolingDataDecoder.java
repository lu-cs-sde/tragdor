package tragdor.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import codeprober.protocol.BinaryInputStream.DataInputStreamWrapper;

public class StringPoolingDataDecoder extends DataInputStreamWrapper {

	private final List<String> stringPool = new ArrayList<>();

	public StringPoolingDataDecoder(byte[] srcBytes) {
		super(new DataInputStream(new ByteArrayInputStream(srcBytes)));

		// Reserve space for pool offset indicator
		try {
			final int poolOffset = super.readInt();

			final ByteArrayInputStream innerBais = new ByteArrayInputStream(srcBytes);
			innerBais.skip(poolOffset);
			final DataInputStream poolReader = new DataInputStream(innerBais);

			final int numPoolEntries = poolReader.readInt();
//			System.out.println("StringPool starts at " + poolOffset + ", contains " + numPoolEntries + " entries");
			for (int i = 0; i < numPoolEntries; ++i) {
				stringPool.add(poolReader.readUTF());
			}
		} catch (IOException impossible) {
			impossible.printStackTrace();
		}
	}

	@Override
	public String readUTF() throws IOException {
		final int stringId = super.readInt();
//		if (stringId >= stringPool.size()) {
//			System.out.println("Read invalid stringPool entry at " + bais.getPosition());
//		}
		return stringPool.get(stringId);
	}
}
