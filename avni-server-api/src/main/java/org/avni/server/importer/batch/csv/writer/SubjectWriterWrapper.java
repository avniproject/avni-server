package org.avni.server.importer.batch.csv.writer;

import org.avni.server.importer.batch.model.Row;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@StepScope
@Component
public class SubjectWriterWrapper implements ItemWriter<Row>, Serializable {
    private final SubjectWriter subjectWriter;

    @Value("#{jobParameters['type']}")
    private String type;

    @Autowired
    public SubjectWriterWrapper(SubjectWriter subjectWriter) {
        this.subjectWriter = subjectWriter;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws Exception {
        this.subjectWriter.write(chunk, type);
    }
}
