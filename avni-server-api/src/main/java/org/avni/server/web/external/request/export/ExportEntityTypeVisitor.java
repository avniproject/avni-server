package org.avni.server.web.external.request.export;

public interface ExportEntityTypeVisitor {
    void visitSubject(ExportEntityType subject);
    void visitEncounter(ExportEntityType encounter, ExportEntityType subject);
    void visitGroup(ExportEntityType groupSubject);
    void visitGroupEncounter(ExportEntityType groupEncounter, ExportEntityType groupSubject);
    void visitProgram(ExportEntityType program, ExportEntityType subject);
    void visitProgramEncounter(ExportEntityType exportEntityType, ExportEntityType program, ExportEntityType subject);
}
