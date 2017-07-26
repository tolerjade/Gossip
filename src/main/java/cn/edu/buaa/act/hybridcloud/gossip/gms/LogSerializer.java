package cn.edu.buaa.act.hybridcloud.gossip.gms;

import cn.edu.buaa.act.hybridcloud.gossip.io.IVersionedSerializer;
import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by toler on 2016/10/12.
 */
public class LogSerializer implements IVersionedSerializer<LogProtos.Log>{
    @Override
    public void serialize(LogProtos.Log log, DataOutput dos) throws IOException {
        byte[] bytes = log.toByteArray();
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    @Override
    public LogProtos.Log deserialize(DataInput dis) throws IOException {
        int size = dis.readInt();
        byte[] bytes = new byte[size];
        dis.readFully(bytes);
        return LogProtos.Log.parseFrom(bytes);
    }

    @Override
    public long serializedSize(LogProtos.Log log) {
        throw new UnsupportedOperationException();
    }
}
