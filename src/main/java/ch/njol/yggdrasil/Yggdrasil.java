/*
 *   This file is part of Yggdrasil, a data format to store object graphs.
 *
 *  Yggdrasil is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Yggdrasil is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2013 Peter Güttinger
 * 
 */

package ch.njol.yggdrasil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import ch.njol.yggdrasil.Fields.FieldContext;
import ch.njol.yggdrasil.xml.YggXMLInputStream;
import ch.njol.yggdrasil.xml.YggXMLOutputStream;

public final class Yggdrasil {
	
	private final List<ClassResolver> classResolvers = new ArrayList<ClassResolver>();
	private final Deque<FieldHandler> fieldHandlers = new LinkedList<FieldHandler>();
	
	private final SimpleClassResolver simpleClassResolver = new SimpleClassResolver();
	
	public Yggdrasil() {
		classResolvers.add(new JRESerializer());
		classResolvers.add(simpleClassResolver);
		fieldHandlers.addLast(new JREFieldHandler());
	}
	
	public YggdrasilOutputStream newOutputStream(final OutputStream out) throws IOException {
		return new DefaultYggdrasilOutputStream(this, out);
	}
	
	public YggdrasilInputStream newInputStream(final InputStream in) throws IOException {
		return new DefaultYggdrasilInputStream(this, in);
	}
	
	public YggXMLOutputStream newXMLOutputStream(final OutputStream out) throws IOException {
		return new YggXMLOutputStream(this, out);
	}
	
	public YggdrasilInputStream newXMLInputStream(final InputStream in) throws IOException {
		return new YggXMLInputStream(this, in);
	}
	
	public void registerClassResolver(final ClassResolver r) {
		if (!classResolvers.contains(r))
			classResolvers.add(r);
	}
	
	public void registerSingleClass(final Class<?> c, final String id) {
		simpleClassResolver.registerClass(c, id);
	}
	
	public void registerFieldHandler(final FieldHandler h) {
		if (!fieldHandlers.contains(h))
			fieldHandlers.addFirst(h);
	}
	
	public final boolean isSerializable(final Class<?> c) {
		try {
			return c.isPrimitive() || c == Object.class || Enum.class.isAssignableFrom(c) && getIDNoError(c) != null ||
					((YggdrasilSerializable.class.isAssignableFrom(c) || getSerializer(c) != null) && newInstance(c) != c);// whatever, just make true out if it (null is a valid return value)
		} catch (final StreamCorruptedException e) { // thrown by newInstance if the class does not provide a correct constructor or is abstract
			return false;
		} catch (final NotSerializableException e) {
			return false;
		}
	}
	
	YggdrasilSerializer<?> getSerializer(final Class<?> c) {
		for (final ClassResolver r : classResolvers) {
			if (r instanceof YggdrasilSerializer && r.getID(c) != null)
				return (YggdrasilSerializer<?>) r;
		}
		return null;
	}
	
	public Class<?> getClass(final String id) throws StreamCorruptedException {
		if ("Object".equals(id))
			return Object.class;
		for (final ClassResolver r : classResolvers) {
			final Class<?> c = r.getClass(id);
			if (c != null) { // TODO error if not serialisable?
				assert Tag.byName(id) == null && (Tag.getType(c) == Tag.T_OBJECT || Tag.getType(c) == Tag.T_ENUM) : "Tag IDs should not be matched: " + id + " (class resolver: " + r + ")";
				assert id.equals(r.getID(c)) : r + " returned " + c + " for id " + id + ", but returns id " + r.getID(c) + " for that class";
				return c;
			}
		}
		throw new StreamCorruptedException("No class found for ID " + id);
	}
	
	private String getIDNoError(Class<?> c) {
		if (c == Object.class)
			return "Object";
		assert Tag.getType(c) == Tag.T_OBJECT || Tag.getType(c) == Tag.T_ENUM;
		if (Enum.class.isAssignableFrom(c) && c.getSuperclass() != Enum.class)
			c = c.getSuperclass();
		for (final ClassResolver r : classResolvers) {
			final String id = r.getID(c);
			if (id != null) {
				assert Tag.byName(id) == null : "Class IDs should not match Tag IDs: " + id + " (class resolver: " + r + ")";
				assert (r instanceof YggdrasilSerializer ? id.equals(r.getID(r.getClass(id))) : r.getClass(id) == c) : r + " returned id " + id + " for " + c + ", but returns " + r.getClass(id) + " for that id";
				return id;
			}
		}
		return null;
	}
	
	public String getID(final Class<?> c) throws NotSerializableException {
		final String id = getIDNoError(c);
		if (id == null)
			throw new NotSerializableException("No ID found for " + c);
		if (!isSerializable(c))
			throw new NotSerializableException(c.getCanonicalName());
		return id;
	}
	
	public void missingField(final Object o, final FieldContext field) throws StreamCorruptedException {
		for (final FieldHandler h : fieldHandlers) {
			if (h.missingField(o, field))
				return;
		}
		throw new StreamCorruptedException("Missing field " + field.name + " in class " + o.getClass().getCanonicalName() + " was not handled");
	}
	
	public void incompatibleFieldType(final Object o, final Field f, final FieldContext field) throws StreamCorruptedException {
		for (final FieldHandler h : fieldHandlers) {
			if (h.incompatibleFieldType(o, f, field))
				return;
		}
		throw new StreamCorruptedException("Incompatible field " + f.getName() + " in class " + o.getClass().getCanonicalName() + " of incompatible " + field.getType() + " was not handled");
	}
	
	public final static Field getField(final Class<?> c, final String name) {
		for (Class<?> sc = c; sc != null; sc = sc.getSuperclass()) {
			try {
				final Field f = sc.getDeclaredField(name);
				final int m = f.getModifiers();
				if (Modifier.isStatic(m) || Modifier.isTransient(m))
					continue;
				return f;
			} catch (final SecurityException e) {
				throw new YggdrasilException(e);
			} catch (final NoSuchFieldException e) {}
		}
		return null;
	}
	
	public void saveToFile(final Object o, final File f) throws IOException {
		FileOutputStream fout = null;
		YggdrasilOutputStream yout = null;
		try {
			fout = new FileOutputStream(f);
			yout = newOutputStream(fout);
			yout.writeObject(o);
			yout.flush();
		} finally {
			if (yout != null)
				yout.close();
			if (fout != null)
				fout.close();
		}
	}
	
	public <T> T loadFromFile(final File f, final Class<T> expectedType) throws IOException {
		FileInputStream fin = null;
		YggdrasilInputStream yin = null;
		try {
			fin = new FileInputStream(f);
			yin = newInputStream(fin);
			return yin.readObject(expectedType);
		} finally {
			if (yin != null)
				yin.close();
			if (fin != null)
				fin.close();
		}
	}
	
	private static Method getSerializableConstructor;
	static {
		try {
			getSerializableConstructor = ObjectStreamClass.class.getDeclaredMethod("getSerializableConstructor", Class.class);
			getSerializableConstructor.setAccessible(true);
		} catch (final NoSuchMethodException e) {
			e.printStackTrace();
			assert false;
		} catch (final SecurityException e) {
			e.printStackTrace();
			assert false;
		}
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	final Object newInstance(final Class<?> c) throws StreamCorruptedException, NotSerializableException {
		final YggdrasilSerializer s = getSerializer(c);
		if (s != null) {
			if (!s.canBeInstantiated(c)) { // only used by isSerializable - return null if OK, throw an YggdrasilException if not
				try {
					final Object o = s.deserialize(c, new Fields());
					if (o != null)
						return null;
					throw new YggdrasilException("YggdrasilSerializer " + s + " returned null from deserialize(" + c + ", new Fields())");
				} catch (final StreamCorruptedException e) {
					return null;
				}
			}
			final Object o = s.newInstance(c);
			if (o == null)
				throw new YggdrasilException("YggdrasilSerializer " + s + " returned null from newInstance(" + c + ")");
			return o;
		} else {
			final ObjectStreamClass osc = ObjectStreamClass.lookupAny(c);
			try {
				final Constructor<?> constr;
				try {
					constr = (Constructor<?>) getSerializableConstructor.invoke(osc, c);
				} catch (final IllegalAccessException e) {
					e.printStackTrace();
					assert false;
					return null;
				} catch (final IllegalArgumentException e) {
					e.printStackTrace();
					assert false;
					return null;
				} catch (final InvocationTargetException e) {
					e.printStackTrace();
					assert false;
					return null;
				}
				if (constr == null)
					throw new StreamCorruptedException("Cannot create an instance of " + c);
				constr.setAccessible(true);
				return constr.newInstance();
			} catch (final IllegalAccessException e) {
				e.printStackTrace();
				assert false;
				return null;
			} catch (final IllegalArgumentException e) {
				e.printStackTrace();
				assert false;
				return null;
			} catch (final InvocationTargetException e) {
				throw new RuntimeException(e);
			} catch (final InstantiationException e) {
				throw new StreamCorruptedException("Cannot create an instance of " + c);
			}
		}
	}
	
	// TODO command line, e.g. convert to XML
	public static void main(final String[] args) {
		System.err.println("Command line not supported yet");
		System.exit(1);
	}
	
}
