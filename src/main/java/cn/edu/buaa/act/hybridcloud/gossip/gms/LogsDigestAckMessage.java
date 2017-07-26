package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by toler on 2016/10/12.
 */
public class LogsDigestAckMessage {
    private static IVersionedSerializer<LogsDigestAckMessage> serializer_;
    static {
        serializer_ = new LogsDigestAckMessageSerializer();
    }

    List<RequestDigest> requestDigests = new ArrayList<RequestDigest>();
    List<SubmitDigest> submitDigests = new ArrayList<SubmitDigest>();
    List<LogProtos.Log> logs = new ArrayList<LogProtos.Log>();

    public static IVersionedSerializer<LogsDigestAckMessage> serializer() {
        return serializer_;
    }

    public LogsDigestAckMessage(List<RequestDigest> requestDigests, List<SubmitDigest> submitDigests, List<LogProtos.Log> logs) {
        this.requestDigests = requestDigests;
        this.submitDigests = submitDigests;
        this.logs = logs;
    }

    public List<RequestDigest> getRequestDigests() {
        return requestDigests;
    }

    public List<SubmitDigest> getSubmitDigests() {
        return submitDigests;
    }

    public List<LogProtos.Log> getLogs() {
        return logs;
    }

    public String toString(){
        return "Request:" + requestDigests.toString() + ",SubmitDigets: " + submitDigests.toString() + ",Log:" + logs.size();
    }
}

class LogsDigestAckMessageSerializer implements IVersionedSerializer<LogsDigestAckMessage>{
    @Override
    public void serialize(LogsDigestAckMessage logsDigestAckMessage, DataOutput dos) throws IOException {
        List<RequestDigest>  requestDigests = logsDigestAckMessage.getRequestDigests();
        List<SubmitDigest>  submitDigests = logsDigestAckMessage.getSubmitDigests();
        List<LogProtos.Log> logs = logsDigestAckMessage.getLogs();
        dos.writeInt(requestDigests.size());
        for(int i = 0; i < requestDigests.size(); i++){
            RequestDigest.serializer().serialize(requestDigests.get(i),dos);
        }
        dos.writeBoolean(true);
        dos.writeInt(submitDigests.size());
        for(int i = 0; i < submitDigests.size(); i++){
            SubmitDigest.serializer().serialize(submitDigests.get(i), dos);
        }
        dos.writeBoolean(true);
        dos.writeInt(logs.size());
        LogSerializer serializer = new LogSerializer();
        for(int i = 0; i<logs.size(); i++){
            serializer.serialize(logs.get(i),dos);
        }
    }

    @Override
    public LogsDigestAckMessage deserialize(DataInput dis) throws IOException {
        int size = dis.readInt();
        List<RequestDigest> requestDigests = new ArrayList<RequestDigest>(size);
        for(int i = 0; i < size; i++){
            RequestDigest requestDigest = RequestDigest.serializer().deserialize(dis);
            requestDigests.add(requestDigest);
        }
        dis.readBoolean();
        size = dis.readInt();
        List<SubmitDigest> submitDigests = new ArrayList<SubmitDigest>(size);
        for(int i = 0; i < size; i++){
            SubmitDigest submitDigest = SubmitDigest.serializer().deserialize(dis);
            submitDigests.add(submitDigest);
        }
        dis.readBoolean();
        size = dis.readInt();
        List<LogProtos.Log> logs = new ArrayList<LogProtos.Log>(size);
        LogSerializer logSerializer = new LogSerializer();
        for(int i = 0; i < size; i++){
            LogProtos.Log log = logSerializer.deserialize(dis);
            logs.add(log);
        }
        return new LogsDigestAckMessage(requestDigests, submitDigests, logs);
    }

    @Override
    public long serializedSize(LogsDigestAckMessage logsDigestAckMessage) {
        throw new UnsupportedOperationException();
    }
}
