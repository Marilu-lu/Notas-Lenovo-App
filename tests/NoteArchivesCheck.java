import cl.marilu.notas.NoteArchives;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class NoteArchivesCheck {
    static byte[] zip(String[] names,String[] contents)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){for(int i=0;i<names.length;i++){out.putNextEntry(new ZipEntry(names[i]));out.write(contents[i].getBytes("UTF-8"));out.closeEntry();}}return bytes.toByteArray();}
    static void rejected(File library,File cache,byte[] bytes)throws Exception{int before=library.list().length;boolean failed=false;try{NoteArchives.restore(library,cache,new ByteArrayInputStream(bytes));}catch(Exception e){failed=true;}if(!failed||library.list().length!=before)throw new AssertionError("Rejected backup altered library");}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(Path.of(args[0]),"archive-tests-");File library=Files.createDirectory(root.resolve("library")).toFile(),cache=Files.createDirectory(root.resolve("cache")).toFile();
        Files.writeString(library.toPath().resolve("1001.json"),"{\"title\":\"Original\",\"folder\":\"Estudio/Biología\",\"pages\":{},\"pdf\":\"1001.json.pdf\"}");
        Files.writeString(library.toPath().resolve("1001.json.pdf"),"%PDF-1.4 unit-fixture");
        ByteArrayOutputStream backup=new ByteArrayOutputStream();NoteArchives.exportAll(library,backup);
        if(NoteArchives.restore(library,cache,new ByteArrayInputStream(backup.toByteArray()))!=1)throw new AssertionError("Wrong import count");
        if(!Files.readString(library.toPath().resolve("1001.json")).contains("Original"))throw new AssertionError("Original overwritten");
        File[] notes=library.listFiles((d,n)->n.endsWith(".json"));if(notes.length!=2)throw new AssertionError("Notes missing");
        for(File f:notes){JSONObject j=new JSONObject(Files.readString(f.toPath()));if(!j.getString("folder").equals("Estudio/Biología"))throw new AssertionError("Folder lost");if(!new File(library,j.getString("pdf")).isFile())throw new AssertionError("PDF reference lost");}
        File single=Files.createDirectory(root.resolve("single")).toFile();ByteArrayOutputStream one=new ByteArrayOutputStream();NoteArchives.exportNote(library,new File(library,"1001.json"),one);if(NoteArchives.restore(single,cache,new ByteArrayInputStream(one.toByteArray()))!=1)throw new AssertionError("Single-note archive failed");
        rejected(library,cache,zip(new String[]{"../escape.json"},new String[]{"{}"}));
        rejected(library,cache,zip(new String[]{"3001.json"},new String[]{"not json"}));
        rejected(library,cache,zip(new String[]{"3001.json"},new String[]{"{\"pages\":[],\"title\":\"Bad\"}"}));
        rejected(library,cache,zip(new String[]{"3001.json"},new String[]{"{\"pages\":{\"0\":[{}]}}"}));
        rejected(library,cache,zip(new String[]{"3001.json"},new String[]{"{\"pages\":{\"0\":[{\"points\":[[999999,0]]}]}}"}));
        rejected(library,cache,zip(new String[]{"3001.json"},new String[]{"{\"pdf\":\"3001.json.pdf\"}"}));
        rejected(library,cache,zip(new String[]{"3001.json","3001.json.pdf"},new String[]{"{\"pdf\":\"../foreign.pdf\"}","%PDF-1.4"}));
        rejected(library,cache,zip(new String[]{"3001.json","3001.json.pdf"},new String[]{"{\"pdf\":\"3001.json.pdf\"}","not a pdf"}));
        if(cache.list().length!=0)throw new AssertionError("Restore staging not cleaned");
        System.out.println("Archive checks passed: merge, PDF remapping, metadata, invalid JSON, traversal and rollback.");
    }
}
