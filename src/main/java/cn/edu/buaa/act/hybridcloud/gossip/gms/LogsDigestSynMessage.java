package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by toler on 2016/10/10.
 */
public class LogsDigestSynMessage {
    private static IVersionedSerializer<LogsDigestSynMessage> serializer_;
    static{
        serializer_ = new LogsDigestSynMessageSerializer();
    }
    String memberId_;
    List<SubmitDigest> submitDigests_ = new ArrayList<SubmitDigest>();
    Long maxExecuteRequest_;

    public LogsDigestSynMessage(String memberId_,  List<SubmitDigest> submitDigests, Long maxExecuteRequest) {
        this.memberId_ = memberId_;
        this.submitDigests_ = submitDigests;
        this.maxExecuteRequest_ = maxExecuteRequest;
    }

    public static IVersionedSerializer<LogsDigestSynMessage> serializer() {
        return serializer_;
    }

    public String getMemberId() {
        return memberId_;
    }

    public List<SubmitDigest> getSubmitDigests() {
        return submitDigests_;
    }

    public Long getMaxExecuteRequest() {
        return maxExecuteRequest_;
    }
    public String toString(){
        return "memberId: " + memberId_  + " maxExecuteRequest_: " + maxExecuteRequest_ + " List<SubmitDigest> : " + submitDigests_.toString();
    }
}

class LogsDigestSynMessageSerializer implements IVersionedSerializer<LogsDigestSynMessage> {
    @Override
    public void serialize(LogsDigestSynMessage logsDigestSynMessage, DataOutput dos) throws IOException {
        dos.writeUTF(logsDigestSynMessage.memberId_);
        int size =  logsDigestSynMessage.getSubmitDigests().size();
        dos.writeInt(size);
        for(SubmitDigest sDigest:logsDigestSynMessage.getSubmitDigests()){
            SubmitDigest.serializer().serialize(sDigest, dos);
        }
        dos.writeLong(logsDigestSynMessage.maxExecuteRequest_);
    }

    @Override
    public LogsDigestSynMessage deserialize(DataInput dis) throws IOException {
        String memberId = dis.readUTF();
        int size = dis.readInt();
        List<SubmitDigest> submitDigests = new ArrayList<SubmitDigest>(size);
        for(int i = 0; i < size; i++){
            submitDigests.add(SubmitDigest.serializer().deserialize(dis));
        }
        Long maxExecuteRequest = dis.readLong();
        return new LogsDigestSynMessage(memberId, submitDigests, maxExecuteRequest);
    }

    @Override
    public long serializedSize(LogsDigestSynMessage logsDigestSynMessage) {
        throw new UnsupportedOperationException();
    }
}
