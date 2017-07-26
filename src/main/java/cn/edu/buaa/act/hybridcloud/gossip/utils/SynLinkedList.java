package cn.edu.buaa.act.hybridcloud.gossip.utils;

import cn.edu.buaa.act.hybridcloud.gossip.proto.LogProtos;
import com.google.protobuf.ProtocolStringList;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Created by toler on 2016/10/9.
 */
public class SynLinkedList extends LinkedList<LogProtos.Log> {
    @Override
    public synchronized LogProtos.Log getFirst() {
        return super.size() > 0? super.getFirst():null;
    }

    @Override
    public synchronized LogProtos.Log getLast() {
        return super.size() > 0 ? super.getLast() : null;
    }

    @Override
    public synchronized LogProtos.Log removeFirst() {
        return super.removeFirst();
    }

    @Override
    public synchronized LogProtos.Log removeLast() {
        return super.removeLast();
    }

    @Override
    public synchronized void addFirst(LogProtos.Log log) {
        super.addFirst(log);
    }

    @Override
    public synchronized void addLast(LogProtos.Log log) {
        super.addLast(log);
    }

    @Override
    public synchronized boolean contains(Object o) {
        return super.contains(o);
    }

    public synchronized boolean contains(long TSM){
        for(int i = 0; i< super.size(); i++){
            LogProtos.Log tmp = super.get(i);
            if(tmp.getTSM() == TSM){
                return true;
            }else if(tmp.getTSM() < TSM){
                continue;
            }else{
                return false;
            }
        }
        return false;
    }

    public synchronized LogProtos.Log getByTSM(long TSM){
        for(int i = 0; i < super.size(); i++){
            LogProtos.Log tmp = super.get(i);
            if(tmp.getTSM() == TSM){
                return tmp;
            }else if(tmp.getTSM() > TSM){
                break;
            }
        }
        return null;
    }
    public synchronized LogProtos.Log getByTOSN(long TOSN){
        for(int i = 0; i < super.size(); i++){
            LogProtos.Log tmp = super.get(i);
            if(tmp.getTOSN() == TOSN){
                return tmp;
            }else if(tmp.getTOSN() > TOSN){
                break;
            }
        }
        return null;
    }

    public synchronized void examine(long remoteMaxTOSN, Collection<String> members){
        for(int i = 0; i < super.size(); i++){
            LogProtos.Log.Builder local = super.get(i).toBuilder();
            if(local.getTOSN() <= remoteMaxTOSN){
                ProtocolStringList accepted = local.getReceivedList();
                for(String member: members){
                    if(!accepted.contains(member)){
                         local.addReceived(member);
                    }
                }
                this.set(i, local.build());
            }else {
                return;
            }
        }
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized boolean add(LogProtos.Log log) {
        if(log.getTOSN() != -1){
            for(int i = 0; i < super.size(); i++){
                LogProtos.Log tmp = super.get(i);
                if(tmp.getTOSN() == log.getTOSN()){
                    LogProtos.Log.Builder builder = tmp.toBuilder();
                    for(String member : log.getReceivedList()){
                        if(!builder.getReceivedList().contains(member)){
                            builder.addReceived(member);
                        }
                    }
                    super.set(i,builder.build());
                    return true;
                }else if(tmp.getTOSN() > log.getTOSN()){
                    super.add(i,log);
                    return true;
                }
            }
        } else {
          for(int i = 0; i < super.size(); i++){
              LogProtos.Log tmp = super.get(i);
              if(tmp.getTSM() == log.getTSM()){
                  LogProtos.Log.Builder builder = tmp.toBuilder();
                 for(String member : log.getReceivedList()){
                     if(!builder.getReceivedList().contains(member)){
                         builder.addReceived(member);
                     }
                 }
                 super.set(i, builder.build());
                 return true;
              }else if(tmp.getTSM() > log.getTSM()){
                  super.add(i,log);
                  return true;
              }
          }
        }
        super.addLast(log);
        return true;
    }

    @Override
    public synchronized LogProtos.Log get(int index) {
        return super.get(index);
    }

    @Override
    public synchronized LogProtos.Log remove() {
        return super.remove();
    }

    public synchronized boolean remove(LogProtos.Log log) {
        return super.remove(log);
    }

    public synchronized boolean remove1(LogProtos.Log log) {
        for(int i = 0; i < super.size(); i++){

            if(log.getTOSN() == -1 && log.getTSM() == super.get(i).getTSM()){
                super.remove(i);
                return true;
            }else if(log.getTOSN() == super.get(i).getTOSN()){
                super.remove(i);
                return true;
            }
        }
        return false;
    }
}
