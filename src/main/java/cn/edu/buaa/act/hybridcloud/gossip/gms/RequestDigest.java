package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by toler on 2016/10/10.
 */
public class RequestDigest{
    private  static IVersionedSerializer<RequestDigest> serializer;
    static{
        serializer = new RequestDigestSerializer();
    }
    //for every host's Request updates
    String memberId;
    long TTSN;

    public static IVersionedSerializer<RequestDigest> serializer(){
        return serializer;
    }

    public RequestDigest(String memberId, long TTSN){
        this.memberId = memberId;
        this.TTSN = TTSN;
    }

    public String getMemberId() {
        return memberId;
    }

    public long getTTSN() {
        return TTSN;
    }

    public String toString(){
        return  "Request : (memberId: "+ memberId + ", TTSN: " + TTSN +")";
    }
}
class RequestDigestSerializer implements IVersionedSerializer<RequestDigest>{
    @Override
    public void serialize(RequestDigest RequestDigest, DataOutput dos) throws IOException {
        dos.writeUTF(RequestDigest.getMemberId());
        dos.writeLong(RequestDigest.getTTSN());
    }

    @Override
    public RequestDigest deserialize(DataInput dis) throws IOException {
        String memberId = dis.readUTF();
        long TTSN = dis.readLong();
        return new RequestDigest(memberId, TTSN);
    }

    @Override
    public long serializedSize(RequestDigest requestDigest) {
        throw new UnsupportedOperationException();
    }
}
