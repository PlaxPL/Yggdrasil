package ch.njol.yggdrasil;

import ch.njol.util.coll.CollectionUtils;

class JREClassResolver implements ClassResolver {
	
	/**
	 * all java packages (copied from the Java 7 docs: http://docs.oracle.com/javase/7/docs/api/)
	 * <p>
	 * Ordered by most important to least important (e.g. <tt>java.util.List</tt> overrides <tt>java.awt.List</tt>)
	 * <p>
	 * excluded: <tt>org.omg.*</tt>
	 */
	private final static String[] packages = {
			"java.util",
			"java.util.concurrent",
			"java.util.concurrent.atomic",
			"java.util.concurrent.locks",
			"java.util.jar",
			"java.util.logging",
			"java.util.prefs",
			"java.util.regex",
			"java.util.spi",
			"java.util.zip",
			
			"java.lang",
			"java.lang.annotation",
			"java.lang.instrument",
			"java.lang.invoke",
			"java.lang.management",
			"java.lang.ref",
			"java.lang.reflect",
			
			"java.math",
			
			"java.io",
			"java.nio",
			"java.nio.channels",
			"java.nio.channels.spi",
			"java.nio.charset",
			"java.nio.charset.spi",
			"java.nio.file",
			"java.nio.file.attribute",
			"java.nio.file.spi",
			"java.net",
			"java.beans",
			"java.beans.beancontext",
			
			"java.security",
			"java.security.acl",
			"java.security.cert",
			"java.security.interfaces",
			"java.security.spec",
			
			"java.sql",
			
			"java.text",
			"java.text.spi",
			
			"java.applet",
			"java.awt",
			"java.awt.color",
			"java.awt.datatransfer",
			"java.awt.dnd",
			"java.awt.event",
			"java.awt.font",
			"java.awt.geom",
			"java.awt.im",
			"java.awt.im.spi",
			"java.awt.image",
			"java.awt.image.renderable",
			"java.awt.print",
			"java.rmi",
			"java.rmi.activation",
			"java.rmi.dgc",
			"java.rmi.registry",
			"java.rmi.server",
			"javax.accessibility",
			"javax.activation",
			"javax.activity",
			"javax.annotation",
			"javax.annotation.processing",
			"javax.crypto",
			"javax.crypto.interfaces",
			"javax.crypto.spec",
			"javax.imageio",
			"javax.imageio.event",
			"javax.imageio.metadata",
			"javax.imageio.plugins.bmp",
			"javax.imageio.plugins.jpeg",
			"javax.imageio.spi",
			"javax.imageio.stream",
			"javax.jws",
			"javax.jws.soap",
			"javax.lang.model",
			"javax.lang.model.element",
			"javax.lang.model.type",
			"javax.lang.model.util",
			"javax.management",
			"javax.management.loading",
			"javax.management.modelmbean",
			"javax.management.monitor",
			"javax.management.openmbean",
			"javax.management.relation",
			"javax.management.remote",
			"javax.management.remote.rmi",
			"javax.management.timer",
			"javax.naming",
			"javax.naming.directory",
			"javax.naming.event",
			"javax.naming.ldap",
			"javax.naming.spi",
			"javax.net",
			"javax.net.ssl",
			"javax.print",
			"javax.print.attribute",
			"javax.print.attribute.standard",
			"javax.print.event",
			"javax.rmi",
			"javax.rmi.CORBA",
			"javax.rmi.ssl",
			"javax.script",
			"javax.security.auth",
			"javax.security.auth.callback",
			"javax.security.auth.kerberos",
			"javax.security.auth.login",
			"javax.security.auth.spi",
			"javax.security.auth.x500",
			"javax.security.cert",
			"javax.security.sasl",
			"javax.sound.midi",
			"javax.sound.midi.spi",
			"javax.sound.sampled",
			"javax.sound.sampled.spi",
			"javax.sql",
			"javax.sql.rowset",
			"javax.sql.rowset.serial",
			"javax.sql.rowset.spi",
			"javax.swing",
			"javax.swing.border",
			"javax.swing.colorchooser",
			"javax.swing.event",
			"javax.swing.filechooser",
			"javax.swing.plaf",
			"javax.swing.plaf.basic",
			"javax.swing.plaf.metal",
			"javax.swing.plaf.multi",
			"javax.swing.plaf.nimbus",
			"javax.swing.plaf.synth",
			"javax.swing.table",
			"javax.swing.text",
			"javax.swing.text.html",
			"javax.swing.text.html.parser",
			"javax.swing.text.rtf",
			"javax.swing.tree",
			"javax.swing.undo",
			"javax.tools",
			"javax.transaction",
			"javax.transaction.xa",
			"javax.xml",
			"javax.xml.bind",
			"javax.xml.bind.Annotation",
			"javax.xml.bind.annotation.adapters",
			"javax.xml.bind.attachment",
			"javax.xml.bind.helpers",
			"javax.xml.bind.util",
			"javax.xml.crypto",
			"javax.xml.crypto.dom",
			"javax.xml.crypto.dsig",
			"javax.xml.crypto.dsig.dom",
			"javax.xml.crypto.dsig.keyinfo",
			"javax.xml.crypto.dsig.spec",
			"javax.xml.datatype",
			"javax.xml.namespace",
			"javax.xml.parsers",
			"javax.xml.soap",
			"javax.xml.stream",
			"javax.xml.stream.events",
			"javax.xml.stream.util",
			"javax.xml.transform",
			"javax.xml.transform.dom",
			"javax.xml.transform.sax",
			"javax.xml.transform.stax",
			"javax.xml.transform.stream",
			"javax.xml.validation",
			"javax.xml.ws",
			"javax.xml.ws.handler",
			"javax.xml.ws.handler.soap",
			"javax.xml.ws.http",
			"javax.xml.ws.soap",
			"javax.xml.ws.spi",
			"javax.xml.ws.spi.http",
			"javax.xml.ws.wsaddressing",
			"javax.xml.xpath",
			"org.ietf.jgss",
			"org.w3c.dom",
			"org.w3c.dom.bootstrap",
			"org.w3c.dom.events",
			"org.w3c.dom.ls",
			"org.xml.sax",
			"org.xml.sax.ext",
			"org.xml.sax.helpers"
	};
	
	@Override
	public Class<?> getClass(final String id) {
		for (final String p : packages) {
			try {
				return Class.forName(p + "." + id);
			} catch (final ClassNotFoundException e) {}
		}
		return null;
	}
	
	@Override
	public String getID(final Class<?> c) {
		if (CollectionUtils.contains(packages, c.getPackage().getName()))
			return c.getSimpleName();
		return null;
	}
	
}