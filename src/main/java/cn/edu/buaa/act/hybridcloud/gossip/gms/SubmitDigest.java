package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;
import cn.edu.buaa.act.hybridcloud.gossip.net.CompactEndpointSerializationHelper;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by toler on 2016/10/10.
 */
public class SubmitDigest implements Comparable<SubmitDigest>{
    private static IVersionedSerializer<SubmitDigest> serializer;
    static{
        serializer = new SubmitDigestSerializer();
    }
    long TOSN;
    String memberId = new String();
    long TSM;
    Set<InetSocketAddress> accepted = new HashSet<InetSocketAddress>();

    public SubmitDigest(long TOSN, long TSM, String memberId, Set<InetSocketAddress> accepted) {
        this.TOSN = TOSN;
        this.TSM = TSM;
        this.memberId = memberId;
        this.accepted = accepted;
    }

    public static IVersionedSerializer<SubmitDigest> serializer() {
        return serializer;
    }

    public long getTOSN() {
        return TOSN;
    }

    public String getMemberId(){
        return this.memberId;
    }

    public long getTSM(){
        return TSM;
    }

    public Set<InetSocketAddress> getAccepted() {
        return accepted;
    }

    @Override
    public String toString() {
        return "TOSN: " + TOSN + " ,memberId: " + memberId + " ,TSM: " + TSM  + " ,accepted:" + accepted;
    }

    @Override
    public int compareTo(SubmitDigest o) {
        return (int) (this.TOSN - o.getTOSN());
    }
}
class SubmitDigestSerializer implements IVersionedSerializer<SubmitDigest>{
    @Override
    public void serialize(SubmitDigest submitDigest, DataOutput dos) throws IOException {
        dos.writeLong(submitDigest.TOSN);
        dos.writeLong(submitDigest.TSM);
        dos.writeUTF(submitDigest.memberId);
        Set<InetSocketAddress> sDigest = submitDigest.accepted;
        int size = sDigest.size();
        dos.writeInt(size);
        for(InetSocketAddress ep: sDigest){
            CompactEndpointSerializationHelper.serialize(ep, dos);
        }
    }

    @Override
    public SubmitDigest deserialize(DataInput dis) throws IOException {
        long TOSN = dis.readLong();
        long TSM = dis.readLong();
        String memberId = dis.readUTF();
        int size = dis.readInt();
        Set<InetSocketAddress> sDigest = new HashSet<InetSocketAddress>(size);
        for(int i = 0; i < size; i++){
            sDigest.add(CompactEndpointSerializationHelper.deserialize(dis));
        }
        return new SubmitDigest(TOSN, TSM,memberId, sDigest);
    }

    @Override
    public long serializedSize(SubmitDigest submitDigest) {
        throw new UnsupportedOperationException();
    }
}
