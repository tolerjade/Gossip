package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by toler on 2016/10/17.
 */
public class LogsDigestAck2Message {
    private static IVersionedSerializer<LogsDigestAck2Message> serializer_;
    static{
        serializer_ = new LogsDigestAck2MessageSerializer();
    }

    List<LogProtos.Log> logs = new ArrayList<LogProtos.Log>();

    public static IVersionedSerializer<LogsDigestAck2Message> serializer(){
        return  serializer_;
    }

    public LogsDigestAck2Message(List<LogProtos.Log> logs) {
        this.logs = logs;
    }

    public List<LogProtos.Log> getLogs() {
        return logs;
    }
}

class LogsDigestAck2MessageSerializer implements IVersionedSerializer<LogsDigestAck2Message>{


    @Override
    public void serialize(LogsDigestAck2Message logsDigestAck2Message, DataOutput dos) throws IOException {
        List<LogProtos.Log> logs = logsDigestAck2Message.getLogs();
        dos.writeInt(logs.size());
        LogSerializer serializer = new LogSerializer();
        for(int i = 0; i<logs.size(); i++){
            serializer.serialize(logs.get(i),dos);
        }
    }

    @Override
    public LogsDigestAck2Message deserialize(DataInput dis) throws IOException {
        int size = dis.readInt();
        List<LogProtos.Log> logs = new ArrayList<LogProtos.Log>(size);
        LogSerializer logSerializer = new LogSerializer();
        for(int i = 0; i < size; i++){
            LogProtos.Log log = logSerializer.deserialize(dis);
            logs.add(log);
        }
        return new LogsDigestAck2Message(logs);
    }

    @Override
    public long serializedSize(LogsDigestAck2Message logsDigestAck2Message) {
        throw new UnsupportedOperationException();
    }
}
