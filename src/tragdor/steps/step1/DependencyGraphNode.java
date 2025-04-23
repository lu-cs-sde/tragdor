package tragdor.steps.step1;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.protocol.BinaryInputStream;
import codeprober.protocol.BinaryOutputStream;
import tragdor.LocatedProp;

public class DependencyGraphNode {

	public final LocatedProp identity;
	public final Set<DependencyGraphNode> incomingEdges = new HashSet<>();
	public final Set<DependencyGraphNode> outgoingEdges = new HashSet<>();

	public DependencyGraphNode(LocatedProp lp) {
		this.identity = lp;
	}

	public String simpleIdentity() {
		final String[] typeParts = identity.locator.result.type.split("\\.");
		return String.format("%s.%s",
				typeParts.length == 0 ? identity.locator.result.type : typeParts[typeParts.length - 1],
				identity.prop.name);
	}

	private boolean copyStarted = false;

	public DependencyGraphNode copy(Function<LocatedProp, DependencyGraphNode> createFresh) {
		final DependencyGraphNode ret = createFresh.apply(identity);
		if (ret.copyStarted) {
			return ret;
		}
		ret.copyStarted = true;
		for (DependencyGraphNode out : outgoingEdges) {
			ret.addOutgoingEdge(out.copy(createFresh));
		}
		return ret;
	}

	public void addOutgoingEdge(DependencyGraphNode to) {
		if (this == to) {
			System.out.println("?? Graph to itself??");
			System.out.println("This: " + this);
			Thread.dumpStack();
			return; // TODO investigate why this happens rather than just return
		}
		outgoingEdges.add(to);
		to.incomingEdges.add(this);
	}

	@Override
	public int hashCode() {
		return identity.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DependencyGraphNode)) {
			return false;
		}
		return identity.equals(((DependencyGraphNode) other).identity);
	}

	@Override
	public String toString() {
		return identity.toString();
	}

	public static JSONObject toJSON(List<DependencyGraphNode> nodes) {
		final Map<DependencyGraphNode, Integer> nodeIndexes = new HashMap<>();
		for (int i = 0; i < nodes.size(); ++i) {
			nodeIndexes.put(nodes.get(i), i);
		}
		final JSONArray arr = new JSONArray();
		for (int i = 0; i < nodes.size(); ++i) {
			final DependencyGraphNode node = nodes.get(i);
			arr.put(new JSONObject() //
					.put("id", node.identity.toJSON()) //
					.put("out", new JSONArray(
							node.outgoingEdges.stream().map(oe -> nodeIndexes.get(oe)).collect(Collectors.toList()))) //

			);
		}
		return new JSONObject().put("nodes", arr);

	}

	public static List<DependencyGraphNode> fromJSON(JSONObject src) {
		final JSONArray jsonArr = src.getJSONArray("nodes");
		final List<DependencyGraphNode> ret = new ArrayList<>();
		for (int i = 0; i < jsonArr.length(); ++i) {
			final JSONObject obj = jsonArr.getJSONObject(i);
			ret.add(new DependencyGraphNode(LocatedProp.fromJSON(obj.getJSONObject("id"))));
		}
		for (int i = 0; i < jsonArr.length(); ++i) {
			final JSONObject obj = jsonArr.getJSONObject(i);
			final DependencyGraphNode node = ret.get(i);
			final JSONArray outs = obj.getJSONArray("out");
			for (int j = 0; j < outs.length(); ++j) {
				final int outIdx = outs.getInt(j);
				node.addOutgoingEdge(ret.get(outIdx));
			}
		}
		return ret;
	}

	public static List<DependencyGraphNode> getAllFrom(BinaryInputStream dis) throws IOException {
		final List<DependencyGraphNode> ret = new ArrayList<>();
		final int numNodes = dis.readInt();
		System.out.println("Num nodes: " + numNodes);
		for (int i = 0; i < numNodes; ++i) {
//			if (numNodes > 1000 && (i % (numNodes / 100)) == 0) {
//				System.out.println("Reading node " + i + "/" + numNodes);
//			}
			ret.add(new DependencyGraphNode(new LocatedProp(dis)));
		}
		for (int srcIdx = 0; srcIdx < numNodes; ++srcIdx) {
			DependencyGraphNode src = ret.get(srcIdx);

			final int numEdges = dis.readInt();
			for (int j = 0; j < numEdges; ++j) {
				src.addOutgoingEdge(ret.get(dis.readInt()));
			}
		}
		return ret;
	}

	public static void writeAllTo(Collection<DependencyGraphNode> nodes, BinaryOutputStream dst) throws IOException {
		dst.writeInt(nodes.size());
		final Map<DependencyGraphNode, Integer> nodeIndexes = new HashMap<>();
		int nodeIndex = 0;
		for (DependencyGraphNode node : nodes) {
			nodeIndexes.put(node, nodeIndex++);
		}
		for (DependencyGraphNode node : nodes) {
			node.identity.writeTo(dst);
		}
		for (DependencyGraphNode node : nodes) {
			dst.writeInt(node.outgoingEdges.size());
			for (DependencyGraphNode tgt : node.outgoingEdges) {
				dst.writeInt(nodeIndexes.get(tgt));
			}
		}
	}

	public static List<DependencyGraphNode> getAllFrom(DataInputStream dis) throws IOException {
		final List<DependencyGraphNode> ret = new ArrayList<>();
		final int numNodes = dis.readInt();
		System.out.println("Num nodes: " + numNodes);
		for (int i = 0; i < numNodes; ++i) {
			if (numNodes > 1000 && (i % (numNodes / 100)) == 0) {
				System.out.println("Reading node " + i + "/" + numNodes);
			}
			ret.add(new DependencyGraphNode(new LocatedProp(dis)));
		}
		for (int srcIdx = 0; srcIdx < numNodes; ++srcIdx) {
			DependencyGraphNode src = ret.get(srcIdx);

			final int numEdges = dis.readInt();
			for (int j = 0; j < numEdges; ++j) {
				src.addOutgoingEdge(ret.get(dis.readInt()));
			}
		}
		return ret;
	}

	public static void writeAllTo(List<DependencyGraphNode> nodes, DataOutputStream dst) throws IOException {
		dst.writeInt(nodes.size());
		final Map<DependencyGraphNode, Integer> nodeIndexes = new HashMap<>();
		for (int i = 0; i < nodes.size(); ++i) {
			nodeIndexes.put(nodes.get(i), i);
		}
		for (int i = 0; i < nodes.size(); ++i) {
			final DependencyGraphNode node = nodes.get(i);
			node.identity.writeTo(dst);
		}
		for (int i = 0; i < nodes.size(); ++i) {
			final DependencyGraphNode node = nodes.get(i);
			dst.writeInt(node.outgoingEdges.size());
			for (DependencyGraphNode tgt : node.outgoingEdges) {
				dst.writeInt(nodeIndexes.get(tgt));
			}
		}
	}

	public static List<DependencyGraphNode> loadAllWithExternalPropIdentifier(Function<Integer, LocatedProp> identifier,
			DataInputStream dis) throws IOException {
		final List<DependencyGraphNode> ret = new ArrayList<>();
		final int numNodes = dis.readInt();
		System.out.println("Num nodes: " + numNodes);
		for (int i = 0; i < numNodes; ++i) {
			if (numNodes > 1000 && (i % (numNodes / 100)) == 0) {
				System.out.println("Reading node " + i + "/" + numNodes);
			}
			ret.add(new DependencyGraphNode(identifier.apply(dis.readInt())));
		}
		for (int srcIdx = 0; srcIdx < numNodes; ++srcIdx) {
			DependencyGraphNode node = ret.get(srcIdx);

			final int numEdges = dis.readInt();
			for (int j = 0; j < numEdges; ++j) {
				node.addOutgoingEdge(ret.get(dis.readInt()));
			}
		}
		return ret;
	}

	public static void writeAllWithExternalPropIdentifier(Collection<DependencyGraphNode> nodes,
			Function<LocatedProp, Integer> identifier, DataOutputStream dst) throws IOException {
		dst.writeInt(nodes.size());
		final Map<DependencyGraphNode, Integer> nodeIndexes = new HashMap<>();
		int nodeIdx = 0;
		for (DependencyGraphNode node : nodes) {
			nodeIndexes.put(node, nodeIdx++);
		}
		for (DependencyGraphNode node : nodes) {
			dst.writeInt(identifier.apply(node.identity));
		}
		for (DependencyGraphNode node : nodes) {
			dst.writeInt(node.outgoingEdges.size());
			for (DependencyGraphNode tgt : node.outgoingEdges) {
				dst.writeInt(nodeIndexes.get(tgt));
			}
		}
	}

}
