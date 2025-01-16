package org.avni.server.framework.hibernate;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;

import java.io.*;

abstract class AbstractUserType<T> implements UserType<T> {
    @Override
    public T deepCopy(final Object value) throws HibernateException {
        try {
            // use serialization to create a deep copy
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(value);
            oos.flush();
            oos.close();
            bos.close();

            ByteArrayInputStream bais = new ByteArrayInputStream(bos.toByteArray());
            return (T) new ObjectInputStream(bais).readObject();
        } catch (ClassNotFoundException | IOException ex) {
            throw new HibernateException(ex);
        }
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(final Object value) throws HibernateException {
        return (Serializable) this.deepCopy(value);
    }

    @Override
    public T assemble(final Serializable cached, final Object owner) throws HibernateException {
        return (T) this.deepCopy(cached);
    }

    @Override
    public T replace(final Object original, final Object target, final Object owner) throws HibernateException {
        return (T) this.deepCopy(original);
    }

    @Override
    public int hashCode(final Object obj) throws HibernateException {
        return obj.hashCode();
    }

    @Override
    public boolean equals(final T obj1, final T obj2) {
        if (obj1 == null) {
            return obj2 == null;
        }
        return obj1.equals(obj2);
    }
}
