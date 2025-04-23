package tragdor.contrib;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import codeprober.protocol.BinaryInputStream;
import codeprober.protocol.BinaryOutputStream;
import tragdor.EvaluatedValue;

public class ReferenceValueDiff {

	public final int listIndex;
	public final EvaluatedValue nonReferenceValue;

	public ReferenceValueDiff(int listIndex, EvaluatedValue nonReferenceValue) {
		this.listIndex = listIndex;
		this.nonReferenceValue = nonReferenceValue;
	}

	public ReferenceValueDiff(BinaryInputStream dis) throws IOException {
		this.listIndex = dis.readInt();
		this.nonReferenceValue = new EvaluatedValue(dis);
	}

	public ReferenceValueDiff(DataInputStream dis) throws IOException {
		this.listIndex = dis.readInt();
		this.nonReferenceValue = new EvaluatedValue(dis);
	}

	public void writeTo(BinaryOutputStream dos) throws IOException {
		dos.writeInt(listIndex);
		nonReferenceValue.writeTo(dos);
	}

	public void writeTo(DataOutputStream dos) throws IOException {
		dos.writeInt(listIndex);
		nonReferenceValue.writeTo(dos);
	}
}
