package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by toler on 2016/10/21.
 */
public class LogRequireTOSNMessage {
    private static IVersionedSerializer<LogRequireTOSNMessage> serializer_;
    static {
        serializer_ = new LogRequireTOSNMessageSericalizer();
    }
    LogProtos.Log log = LogProtos.Log.newBuilder().build();

    public LogRequireTOSNMessage(LogProtos.Log log) {
        this.log = log;
    }

    public static IVersionedSerializer<LogRequireTOSNMessage> serializer(){
        return  serializer_;
    }
    public LogProtos.Log getLog(){
        return log;
    }
}
class LogRequireTOSNMessageSericalizer implements IVersionedSerializer<LogRequireTOSNMessage>{
    @Override
    public void serialize(LogRequireTOSNMessage logRequireTTSNMessage, DataOutput dos) throws IOException {
        LogSerializer serializer = new LogSerializer();
        serializer.serialize(logRequireTTSNMessage.getLog(), dos);
    }

    @Override
    public LogRequireTOSNMessage deserialize(DataInput dis) throws IOException {
        LogSerializer serializer = new LogSerializer();
        LogProtos.Log log = serializer.deserialize(dis);
        return new LogRequireTOSNMessage(log);
    }

    @Override
    public long serializedSize(LogRequireTOSNMessage logRequireTTSNMessage) {
        throw new UnsupportedOperationException();
    }
}
