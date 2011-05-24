package org.apache.maven.plugins.jmx;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.JmxAttribute;
import org.apache.maven.plugins.annotations.JmxBean;
import org.apache.maven.plugins.annotations.JmxMethod;
import org.apache.maven.project.MavenProject;
import org.scannotation.AnnotationDB;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Generates JMX Files
 *
 * @phase process-classes
 * @goal install
 *
 *
  			<!--plugin>
 				<groupId>de.deutschepost.e2p.userservice</groupId>
 				<artifactId>userservice-mojo</artifactId>
 				<version>${project.version}</version>
 				<executions>
					<execution>
						<phase>process-classes</phase>
						<goals>
							<goal>install</goal>
						</goals>
					</execution>
				</executions>
			</plugin-->
 *
 */
public class JmxMojo extends AbstractMojo {

    Log log = new SystemStreamLog();

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @since 1.0-alpha-1
     *
     */
    protected MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        log.info("Execution JMX Mojo");

        if (project != null) {
            File base = project.getBasedir();
            if (base != null) {
                log.info("Basedir is "+base.getAbsolutePath());
            }
        }

        File f = new File("./target/classes");

        try {
            processPath(f);
        } catch (Exception e) {
            log.warn("I was not able to process the JMX Descriptors! Reason "+e.getMessage());
        }


    }

    /**
     * scan entire classpath below f and search for JmxBean annotated classes
     * @param f
     */
    protected void processPath(File f) {
        try {
            int i = 0;
            URL[] u = new URL[1];
            u[0] = f.toURI().toURL();
            AnnotationDB db = new AnnotationDB();
            db.scanArchives(u);
            URLClassLoader cl = URLClassLoader.newInstance(u);

            ClassPool.getDefault().appendClassPath(f.getAbsolutePath());
            ClassPool.getDefault().appendSystemPath();

            String apiPath = f.getAbsolutePath() + "/../../../userservice-api/target/classes/";
            log.info("I am assuming Userservice API at "+apiPath);
            ClassPool.getDefault().appendClassPath
                    (apiPath);

            Set<String> classes = db.getAnnotationIndex().get(JmxBean.class.getName());

            for (String clazz : classes) {
                CtClass cc = ClassPool.getDefault().get(clazz);
                toXml(cc);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    <T> T getAnnotation(CtClass c, Class<T> annot) throws Exception{
        for (Object a : c.getAnnotations()) {
            try {
              return  annot.cast(a);
            } catch (Exception e) {

            }
        }
        return null;
    }


    <T> T getAnnotation(CtMethod c, Class<T> annot) throws Exception{
        for (Object a : c.getAnnotations()) {
            try {
              return  annot.cast(a);
            } catch (Exception e) {

            }
        }
        return null;
    }


    /**
     * create XML files from the passed class.
     * This function will create a -xmbean.xml and a -service.xml. These files are used
     * for deployment.
     *
     * the function divides up into two steps:
     *
     * step 1: gather inforammration from class and create meta data holder
     * step 2: serialize holder data into XML
     *
     * @param c
     * @throws Exception
     */
    protected void toXml(CtClass c) throws Exception {
        JmxBean bean = getAnnotation(c, JmxBean.class);
        log.info("Processing JMX Class "+c.getName());
        log.info(bean == null ? "WARNING! JMX Annotation contained nothing!" :"... here we go!");

        for (Object s : c.getAnnotations()) {
            log.info("Annotations : "+s);
        }

        /* gather class, attrib, method meta infos */

        HashSet<ClassMethod> methods = new HashSet <ClassMethod>();
        HashMap<String,ClassAttr> attribs = new HashMap <String,ClassAttr>();

        for (CtMethod m : c.getMethods()) {
            JmxAttribute attribute = getAnnotation(m, JmxAttribute.class);
            ClassAttr a2 = null;

            String attribName = m.getName();
            boolean getter = attribName.startsWith("get") ;
            boolean setter =  attribName.startsWith("set");
            if (getter || setter ) {
                attribName = attribName.substring(3);
            } else {
                if (attribute != null) { throw new RuntimeException("FAILURE : "+m.getName()+" is neither getter nor " +
                        "setter.");
                }
            }

            a2 = attribs.get(attribName);
            if (a2 != null) {
                annotation2attribute(m, attribute, attribName, getter, setter, a2);

            }  else {
                if (attribute != null) {
                    ClassAttr a = new ClassAttr();
                    annotation2attribute(m, attribute, attribName, getter, setter, a);
                    attribs.put(attribName,a);
                }
            }

            JmxMethod method = getAnnotation(m, JmxMethod.class);
            if (method  !=null) {
                ClassMethod target = new ClassMethod(method.value(),m.getName(),m.getReturnType().getName());
                int i = 0;
                Collection<MethParam> params = new LinkedList<MethParam>();
                for (CtClass param : m.getParameterTypes()) {
                    MethParam p = new MethParam("P"+i, param.getName());
                    params.add(p);
                    ++i;
                }
                if (params.size() > 0) {
                    target.params = params.toArray(new MethParam[params.size()]);
                }
                methods.add(target);
            }
        }

        /* bring meta info into XML files */

        File outputFile = new File("./target/"+c.getSimpleName()+"-xmbean.xml");

        saveConfig(outputFile, c, bean, methods.toArray(new ClassMethod[methods.size()]),
                attribs.values().toArray(new ClassAttr[attribs
                        .values().size()]));

        File serviceFile = new File("./target/"+c.getSimpleName()+"-service.xml");

        saveService(c,bean,serviceFile,outputFile);

    }

    /**
     * this function is used to bring the gathered class information to a -service.xml file
     *
     * @param clazz - the class to export
     * @param bean - the bean annotation found at clazz
     * @param f - the outfile to write
     * @param beanFile - the -xmbean.xml file in order to build the reference
     * @throws Exception
     */
    private void saveService(CtClass clazz, JmxBean bean, File f, File beanFile) throws Exception {
        XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
        // Create XMLEventWriter
        XMLEventWriter eventWriter = outputFactory
                .createXMLEventWriter(new FileOutputStream(f));

        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        XMLEvent end = eventFactory.createDTD("\n");
        XMLEvent tab = eventFactory.createDTD("\t");
        // Create and write Start Tag
        StartDocument startDocument = eventFactory.createStartDocument();
        eventWriter.add(startDocument);

                        // Create config open tag
        StartElement configStartElement = eventFactory.createStartElement("",
                "", "server");
        eventWriter.add(configStartElement);
        eventWriter.add(end);


       List<Attribute> attributeList = new LinkedList<Attribute>();
        List<Attribute> nsList = new LinkedList<Attribute>();

        attributeList.add(eventFactory.createAttribute("code",clazz.getName()));
        attributeList.add(eventFactory.createAttribute("name",bean.value()));
        attributeList.add(eventFactory.createAttribute("xmbean-dd","file:///e2/var/jboss/server/deploy/"+beanFile
                .getName()));

        eventWriter.add(tab);
        StartElement mbean = eventFactory.createStartElement(
                "", "", "mbean", attributeList.iterator(), nsList
                        .iterator());
        eventWriter.add(mbean);
        createNode(eventWriter,"depends","jboss.j2ee:ear=userservice.ear,jar=userservice-impl.jar,name=ProcessRunnerImpl,service=EJB3");

        eventWriter.add(eventFactory.createEndElement("", "",
                "mbean"));
        eventWriter.add(end);

        eventWriter.add(eventFactory.createEndElement("", "", "server"));

        eventWriter.add(eventFactory.createEndDocument());
        eventWriter.close();
    }

    /**
     * parses an attribute and fills out a jmx attribute object
     * @param m - the method
     * @param attribute - the attribute annotation
     * @param attribName - the class attribute name covered by the getter or setter
     * @param getter - is true, if a getter is processed
     * @param setter - is true, if a setter is processed
     * @param a - the attribute instance to fill out
     * @throws Exception
     */
    private void annotation2attribute(CtMethod m, JmxAttribute attribute, String attribName, boolean getter,
            boolean setter, ClassAttr a) throws Exception{
        if (attribute != null) {
            a.desc=attribute.value() != null && a.desc == null ?
                   attribute.value() :
                        attribute.value() != null && a.desc != null ?
                        a.desc += " " +attribute.value() : a.desc;
            a.r |= getter;
            a.w |= setter;
            a.name = attribName;
        }
        if (getter) { a.get = m.getName(); a.type = m.getReturnType().getName(); }
        if (setter) { a.set = m.getName(); }
    }

    /**
     * this function creates a -xmbean.xml file from the passed data
     *
     * @param f - the file where the xml is stored
     * @param clazz - the object which is processed
     * @param bean - the bean annotation
     * @param methods - all found class method info holders
     * @param attribs - all found class attrib info holders
     * @throws Exception
     */
    public void saveConfig(File f,CtClass clazz, JmxBean bean, ClassMethod[] methods,
            ClassAttr[] attribs) throws Exception {
        // Create a XMLOutputFactory
        XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
        // Create XMLEventWriter
        XMLEventWriter eventWriter = outputFactory
                .createXMLEventWriter(new FileOutputStream(f));
        // Create a EventFactory
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        XMLEvent dtd = eventFactory.createDTD("<!DOCTYPE mbean PUBLIC \"-//JBoss//DTD JBOSS XMBEAN 1.0//EN\"  \n" +
                "   \"http://www.jboss.org/j2ee/dtd/jboss_xmbean_1_0.dtd\">");
        XMLEvent end = eventFactory.createDTD("\n");
        // Create and write Start Tag
        StartDocument startDocument = eventFactory.createStartDocument();
        eventWriter.add(startDocument);
        eventWriter.add(end);
        eventWriter.add(dtd);
        eventWriter.add(end);

        // Create config open tag
        StartElement configStartElement = eventFactory.createStartElement("",
                "", "mbean");
        eventWriter.add(configStartElement);
        eventWriter.add(end);

        // Write the different nodes
        if (bean.description() != null) {
            createNode(eventWriter, "description", bean.description());
        }
        createNode(eventWriter, "class", clazz.getName());

        for (ClassAttr a:attribs) {
            createAttribute(eventWriter,clazz,a);
        }

        for (ClassMethod a:methods) {
                createMethod(eventWriter,clazz,a);
        }
        eventWriter.add(eventFactory.createEndElement("", "", "mbean"));
        eventWriter.add(end);
        eventWriter.add(eventFactory.createEndDocument());
        eventWriter.close();
    }

    /**
     * stax helper function, creates a \n\t<opentag>data<closetag> element
     *
     * @param eventWriter
     * @param name
     * @param value
     * @throws XMLStreamException
     */
    private void createNode(XMLEventWriter eventWriter, String name,
            String value) throws XMLStreamException {

        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        XMLEvent end = eventFactory.createDTD("\n");
        XMLEvent tab = eventFactory.createDTD("\t");
        // Create Start node
        StartElement sElement = eventFactory.createStartElement("", "", name);
        eventWriter.add(tab);
        eventWriter.add(sElement);

        // Create Content
        Characters characters = eventFactory.createCharacters(value);
        eventWriter.add(characters);
        // Create End node
        EndElement eElement = eventFactory.createEndElement("", "", name);
        eventWriter.add(eElement);
        eventWriter.add(end);

    }

    /**
     * creates an attribute xml sections
     *
     * @param eventWriter
     * @param clazz
     * @param attrib
     * @throws Exception
     */
    private void createAttribute(XMLEventWriter eventWriter, CtClass clazz, ClassAttr attrib) throws Exception{
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        XMLEvent end = eventFactory.createDTD("\n");
        XMLEvent tab = eventFactory.createDTD("\t");

        String access = attrib.r ? "read" : "";
        access += attrib.w ? (attrib.r ? "-write" : "write-only") : "-only";
        String desc = attrib.desc;
        List<Attribute> attributeList = new LinkedList<Attribute>();
        List<Attribute> nsList = new LinkedList<Attribute>();

        attributeList.add(eventFactory.createAttribute("access",
                access));

        if (attrib.get != null) {
            attributeList.add(eventFactory.createAttribute("getMethod",
                attrib.get));
        }

        if (attrib.set != null) {
            attributeList.add(eventFactory.createAttribute("setMethod",
                attrib.set));
        }

        eventWriter.add(end);
        eventWriter.add(tab);

        StartElement attribute = eventFactory.createStartElement(
                "", "", "attribute", attributeList.iterator(), nsList
                        .iterator());
        eventWriter.add(attribute);
        eventWriter.add(end);
        eventWriter.add(tab);

        EndElement attributetEndElement = eventFactory.createEndElement("", "",
                "attribute");
        createNode(eventWriter,"description",attrib.desc);
        eventWriter.add(tab);
        createNode(eventWriter, "name", attrib.name);
        eventWriter.add(tab);
        createNode(eventWriter, "type", attrib.type);
        eventWriter.add(tab);
        eventWriter.add(attributetEndElement);
        eventWriter.add(end);

    }

    /**
     * creates an operation xml section
     *
     * @param eventWriter
     * @param clazz
     * @param methd
     * @throws Exception
     */
    private void createMethod(XMLEventWriter eventWriter, CtClass clazz, ClassMethod methd) throws Exception{
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        XMLEvent end = eventFactory.createDTD("\n");
        XMLEvent tab = eventFactory.createDTD("\t");

        List<Attribute> attributeList = new LinkedList<Attribute>();
        List<Attribute> nsList = new LinkedList<Attribute>();

        eventWriter.add(tab);
        StartElement operation = eventFactory.createStartElement(
                "", "", "operation", attributeList.iterator(), nsList
                        .iterator());
        eventWriter.add(operation);
        eventWriter.add(end);
        eventWriter.add(tab);
        createNode(eventWriter,"description",methd.description);
        eventWriter.add(tab);
        createNode(eventWriter,"name",methd.name);
        eventWriter.add(tab);
        if (methd.params != null) {
            for (MethParam p : methd.params) {
                eventWriter.add(end);
                eventWriter.add(tab);
                StartElement parameter = eventFactory.createStartElement(
                        "", "", "parameter", attributeList.iterator(), nsList
                                .iterator());
                eventWriter.add(parameter);
                eventWriter.add(end);
                eventWriter.add(tab);
                eventWriter.add(tab);
                createNode(eventWriter, "name", p.name);
                eventWriter.add(tab);
                eventWriter.add(tab);
                createNode(eventWriter, "type", p.type);
                eventWriter.add(tab);
                EndElement parameterEndElement = eventFactory.createEndElement("", "",
                        "parameter");
                eventWriter.add(parameterEndElement);

            }
        }

        eventWriter.add(tab);
        eventWriter.add(end);

        createNode(eventWriter,"return-type",methd.returnType);
        eventWriter.add(tab);
        EndElement operationEndElement = eventFactory.createEndElement("", "",
                "operation");
        eventWriter.add(operationEndElement);
        eventWriter.add(end);

    }

    /**
     * Meta information containers used for XML generation
     */

    private class ClassAttr {
        String set;
        String get;
        boolean r=false;
        boolean w=false;
        String desc;
        String name;
        String type;
    }
    private class MethParam {
            String name;
            String type;

        private MethParam(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private class ClassMethod {
        String description;
        String name;
        String returnType;

        private ClassMethod(String description, String name, String returnType) {
            this.description = description;
            this.name = name;
            this.returnType = returnType;
        }

        MethParam[] params;
    }
}
