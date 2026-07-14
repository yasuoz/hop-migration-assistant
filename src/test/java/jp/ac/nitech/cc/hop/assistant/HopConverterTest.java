package jp.ac.nitech.cc.hop.assistant;

import jp.ac.nitech.cc.hop.assistant.imports.Converter;
import org.junit.Test;

import static org.junit.Assert.*;

public class HopConverterTest {

	@Test
	public void testConverter() {
		{
			final var c1 = Converter.from("import org.pentaho.di.core.variables.VariableSpace;");
			assertEquals("import org.apache.hop.core.variables.IVariables;", c1.toString());
			assertTrue(c1.renamed);
		}
		{
			final var c1 = Converter.from("import org.pentaho.di.core.row.RowMetaInterface;");
			assertEquals("import org.apache.hop.core.row.IRowMeta;", c1.toString());
			assertEquals("RowMetaInterface", c1.getOldName());
			assertEquals("IRowMeta", c1.getNewName());
			assertTrue(c1.renamed);
		}
		{
			final var c1 = Converter.from("import org.pentaho.di.core.RowSet;");
			assertEquals("import org.apache.hop.core.IRowSet;", c1.toString());
			assertEquals("RowSet", c1.getOldName());
			assertEquals("IRowSet", c1.getNewName());
			assertTrue(c1.renamed);
		}
		{
			final var c1 = Converter.from("import static org.pentaho.di.core.util.Utils.isEmpty;");
			assertEquals("import static org.apache.hop.core.util.Utils.isEmpty;", c1.toString());
			assertEquals("isEmpty", c1.getOldName());
			assertEquals("isEmpty", c1.getNewName());
			assertFalse(c1.renamed);
		}
		{
			final var c1 = Converter.from("import org.pentaho.di.trans.step.BaseStep");
			assertEquals("import org.apache.hop.pipeline.transform.BaseTransform;", c1.toString());
			assertEquals("BaseStep", c1.getOldName());
			assertEquals("BaseTransform", c1.getNewName());
			assertTrue(c1.renamed);
		}
		{
			final var c1 = Converter.from("import org.pentaho.di.job.entries.copyfiles.JobEntryCopyFiles");
			assertEquals("import org.apache.hop.workflow.actions.copyfiles.ActionCopyFiles;", c1.toString());
			assertEquals("JobEntryCopyFiles", c1.getOldName());
			assertEquals("ActionCopyFiles", c1.getNewName());
		}
		{
			final var c1 = Converter.from("import org.pentaho.di.repository.kdr.KettleDatabaseRepository");
			assertEquals("import org.apache.hop.metadata.kdr.HopDatabaseRepository;", c1.toString());
		}
		{
			final var c1 = Converter.from("import org.pentaho.di.shared.SharedObjects");
			assertEquals("import org.apache.hop.variables.SharedObjects;", c1.toString());
		}
		{
			final var c5 = Converter.from("import static org.pentaho.di.core.Const.InnerClass.EMPTY_STRING");
			assertEquals("import static org.apache.hop.core.Const.InnerClass.EMPTY_STRING;", c5.toString());
			assertEquals("EMPTY_STRING", c5.getOldName());
			assertEquals("EMPTY_STRING", c5.getNewName());
		}
		{
			final var c6 = Converter.from("import static org.pentaho.di.core.Const.*");
			assertEquals("import static org.apache.hop.core.Const.*;", c6.toString());
			assertEquals("", c6.getOldName());
		}
	}
}
