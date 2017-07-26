package cn.edu.buaa.act.hybridcloud.gossip.config;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;

/**
 * Created by toler on 2016/12/12.
 */
public class ParesConfig {
    //private String resource = System.getProperty("user.dir");
    private String resource = System.getProperty("user.dir");
    private Properties properties = new Properties();
    public ParesConfig(){
        try{
            String fileSeparate = File.separator;
            System.out.println(resource);
            resource += fileSeparate + "gossip.properties";
            //InputStream in = getClass().getClassLoader().getResourceAsStream("gossip.properties");
            InputStream in = new BufferedInputStream(new FileInputStream(this.resource));
            this.properties.load(in);
            Iterator<String> it = this.properties.stringPropertyNames().iterator();
            while (it.hasNext()) {
                String key = it.next();
                System.out.println(key + ":" + properties.getProperty(key));
            }
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
