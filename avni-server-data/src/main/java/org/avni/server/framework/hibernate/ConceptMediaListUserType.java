package org.avni.server.framework.hibernate;

import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.domain.ConceptMedia;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConceptMediaListUserType extends AbstractJsonbUserType<List<ConceptMedia>> {
    @Override
    public Class<List<ConceptMedia>> returnedClass() {
        return (Class<List<ConceptMedia>>) ((Class) List.class);
    }

    @Override
    public List<ConceptMedia> nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String cellContent = rs.getString(position);
        if (cellContent == null || cellContent.trim().isEmpty() || cellContent.equals("{}")) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(cellContent, new TypeReference<List<ConceptMedia>>() {});
        } catch (IOException ex) {
            throw new HibernateException("Failed to deserialize ConceptMedia list", ex);
        }
    }
}
