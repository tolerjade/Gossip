package cn.edu.buaa.act.hybridcloud.gossip.gms;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by toler on 2017/1/9.
 */
public class FileWrite {
    public static final FileWrite instatence = new FileWrite();
    private static Logger logger = Logger.getLogger(FileWrite.class);

    public static void main(String args[]) {
        String resource = System.getProperty("user.dir");
        String path = resource + File.separator + "a" + File.separator + "b" + File.separator + "tmp1.dat";
        System.out.println(resource);
        FileWrite.instatence.writeToFile(path, 15, "qwert".getBytes());
    }

    public void writeToFile(String fileNamePath, long offset, byte[] contents) {
        File file = new File(fileNamePath);
        if (!file.exists()) {
            int directoryNameLen = fileNamePath.lastIndexOf(File.separator);
            String directoryNamePath = fileNamePath.substring(0, directoryNameLen);
            File dir = new File(directoryNamePath);
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //System.out.println(directoryNameLen);
            //System.out.println(fileNamePath.substring(0,directoryNameLen));
        }
        try {
            System.out.println(fileNamePath);
            RandomAccessFile randomAccessFile = new RandomAccessFile(fileNamePath, "rw");
            System.out.println("file write: offset:" + offset + " bytes:" + contents.length);
            randomAccessFile.seek(offset);//实验
            randomAccessFile.write(contents);
            randomAccessFile.close();
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        /*
        File file = new File(fileNamePath);
        BufferedOutputStream bos = null;
        if(!file.exists()){
            try{
                file.createNewFile();
                System.out.println("new File");
            }catch (IOException e){
                logger.error(e.getMessage());
                return;
            }
        }
        try{
            OutputStream os = new FileOutputStream(file,true);
            //bos = new BufferedOutputStream(os);
            os.write(contents,offset,contents.length);
            //bos.flush();
            os.close();
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        */
    }
}
