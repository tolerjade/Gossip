package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by toler on 2016/10/21.
 */
public class LogResponseTOSNMessage {
    private static IVersionedSerializer<LogResponseTOSNMessage> serializer_;

    static {
        serializer_ = new LogResponseTOSNSerializer();
    }
    SubmitDigest submitDigest = new SubmitDigest(-1,-1," ",null);

    public LogResponseTOSNMessage(SubmitDigest submitDigests) {
        this.submitDigest = submitDigests;
    }

    public  SubmitDigest getSubmitDigest(){
        return submitDigest;
    }

    public static IVersionedSerializer<LogResponseTOSNMessage> serializer(){
        return serializer_;
    }
}

class LogResponseTOSNSerializer implements IVersionedSerializer<LogResponseTOSNMessage>{
    @Override
    public void serialize(LogResponseTOSNMessage logResponseTOSNMessage, DataOutput dos) throws IOException {
        SubmitDigest.serializer().serialize(logResponseTOSNMessage.getSubmitDigest(), dos);
    }

    @Override
    public LogResponseTOSNMessage deserialize(DataInput dis) throws IOException {
        SubmitDigest submitDigest = SubmitDigest.serializer().deserialize(dis);
        return new LogResponseTOSNMessage(submitDigest);
    }

    @Override
    public long serializedSize(LogResponseTOSNMessage logResponseTOSNMessage) {
        throw new UnsupportedOperationException();
    }
}
