package com.codewalnut.prreviewer.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.prreviewer.domain.Technology;
import org.junit.jupiter.api.Test;

class FileTechnologyMapperTest {

    @Test
    void javaFileMapsToJavaSpringAndHibernate() {
        assertThat(FileTechnologyMapper.technologiesFor("src/main/java/Order.java"))
                .containsExactlyInAnyOrder(
                        Technology.JAVA, Technology.SPRING, Technology.HIBERNATE, Technology.GENERAL);
    }

    @Test
    void sqlFileMapsToSqlAndMysql() {
        assertThat(FileTechnologyMapper.technologiesFor("db/migration/V1__init.sql"))
                .containsExactlyInAnyOrder(Technology.SQL, Technology.MYSQL, Technology.GENERAL);
    }

    @Test
    void tsxFileMapsToTypescriptReactAndBootstrap() {
        assertThat(FileTechnologyMapper.technologiesFor("src/App.tsx"))
                .containsExactlyInAnyOrder(
                        Technology.TYPESCRIPT, Technology.REACT, Technology.BOOTSTRAP, Technology.GENERAL);
    }

    @Test
    void plainJsFileMapsToJavascriptOnly() {
        assertThat(FileTechnologyMapper.technologiesFor("scripts/build.js"))
                .containsExactlyInAnyOrder(Technology.JAVASCRIPT, Technology.GENERAL);
    }

    @Test
    void unknownExtensionStillGetsGeneral() {
        assertThat(FileTechnologyMapper.technologiesFor("README.md")).containsExactly(Technology.GENERAL);
    }

    @Test
    void nullPathStillGetsGeneral() {
        assertThat(FileTechnologyMapper.technologiesFor(null)).containsExactly(Technology.GENERAL);
    }
}
