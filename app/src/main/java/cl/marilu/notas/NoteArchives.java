package cl.marilu.notas;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.file.Files;

/** Portable ZIP backup. Imports merge into fresh IDs and never overwrite notes. */
public final class NoteArchives {
    private static final long MAX_TOTAL=128L*1024*1024, MAX_ENTRY=32L*1024*1024;
    private static boolean allowed(String n){return n.matches("[0-9]+\\.json(\\.pdf)?");}
    private static void validate(JSONObject note)throws Exception{
        if(note.has("pages")&&!(note.get("pages") instanceof JSONObject))throw new IOException("Páginas no válidas");
        int count=note.optInt("pageCount",1);if(count<1||count>5000)throw new IOException("Número de páginas no válido");
        JSONObject pages=note.optJSONObject("pages");if(pages==null)return;
        Iterator<String> keys=pages.keys();while(keys.hasNext()){
            String key=keys.next();if(!key.matches("[0-9]{1,5}")||Integer.parseInt(key)>4999)throw new IOException("Índice de página no válido");
            JSONArray strokes=pages.optJSONArray(key);if(strokes==null||strokes.length()>100_000)throw new IOException("Trazos no válidos");
            for(int i=0;i<strokes.length();i++){
                JSONObject stroke=strokes.optJSONObject(i);if(stroke==null)throw new IOException("Trazo no válido");
                JSONArray points=stroke.optJSONArray("points");if(points==null||points.length()==0||points.length()>1_000_000)throw new IOException("Puntos no válidos");
                for(int p=0;p<points.length();p++){JSONArray point=points.optJSONArray(p);if(point==null||point.length()<2)throw new IOException("Coordenadas no válidas");for(int axis=0;axis<2;axis++){double v=point.getDouble(axis);if(!Double.isFinite(v)||Math.abs(v)>100)throw new IOException("Coordenadas fuera de rango");}}
                if(stroke.has("image")&&points.length()!=2)throw new IOException("Imagen no válida");
            }
        }
    }
    private static void copy(InputStream in,OutputStream out) throws IOException {byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    public static void exportAll(File directory, OutputStream target) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(target)) {
            File[] files=directory.listFiles((d,n)->allowed(n)); if(files==null)throw new IOException("No se pudo leer la biblioteca");
            for(File f:files){zip.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){copy(in,zip);}zip.closeEntry();}
        }
    }
    public static void exportNote(File directory,File note,OutputStream target)throws Exception{
        if(!note.getParentFile().equals(directory)||!allowed(note.getName())||!note.getName().endsWith(".json"))throw new IOException("Nota no válida");
        JSONObject value=new JSONObject(new String(Files.readAllBytes(note.toPath()),java.nio.charset.StandardCharsets.UTF_8));String attachment=value.optString("pdf");
        try(ZipOutputStream zip=new ZipOutputStream(target)){
            ArrayList<File> files=new ArrayList<>();files.add(note);if(!attachment.isEmpty()){if(!attachment.equals(note.getName()+".pdf"))throw new IOException("Referencia PDF no válida");files.add(new File(directory,attachment));}
            for(File f:files){zip.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){copy(in,zip);}zip.closeEntry();}
        }
    }
    public static int restore(File directory,File cache,InputStream source) throws Exception {
        File stage=Files.createTempDirectory(cache.toPath(),"restore-").toFile();
        ArrayList<File> written=new ArrayList<>();
        try {
            long total=0;Set<String> names=new HashSet<>();
            try(ZipInputStream zip=new ZipInputStream(source)) {
                ZipEntry e;byte[] b=new byte[8192];
                while((e=zip.getNextEntry())!=null){
                    String name=e.getName();if(e.isDirectory()||!allowed(name)||!names.add(name)||names.size()>2000)throw new IOException("Archivo de copia no válido");
                    long size=0;try(OutputStream out=new FileOutputStream(new File(stage,name))){int n;while((n=zip.read(b))!=-1){size+=n;total+=n;if(size>MAX_ENTRY||total>MAX_TOTAL)throw new IOException("Copia demasiado grande (máximo 128 MB)");out.write(b,0,n);}}
                }
            }
            File[] notes=stage.listFiles((d,n)->n.endsWith(".json"));if(notes==null||notes.length==0)throw new IOException("La copia no contiene notas");
            LinkedHashMap<File,byte[]> plan=new LinkedHashMap<>();long id=System.currentTimeMillis();
            for(File f:notes){
                JSONObject note=new JSONObject(new String(Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8));
                validate(note);
                File dest;do{dest=new File(directory,(id++)+".json");}while(dest.exists());
                String pdf=note.optString("pdf","");
                if(!pdf.isEmpty()){
                    if(!pdf.equals(f.getName()+".pdf"))throw new IOException("Referencia PDF no válida");
                    File attachment=new File(stage,pdf);if(!attachment.isFile())throw new IOException("Falta un PDF en la copia");
                    byte[] bytes=Files.readAllBytes(attachment.toPath());if(bytes.length<5||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("PDF no válido");
                    note.put("pdf",dest.getName()+".pdf");plan.put(new File(directory,dest.getName()+".pdf"),bytes);
                }
                plan.put(dest,note.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            for(Map.Entry<File,byte[]> e:plan.entrySet()){
                written.add(e.getKey());try(OutputStream out=new FileOutputStream(e.getKey())){out.write(e.getValue());}
            }
            return notes.length;
        }catch(Exception e){for(File f:written)f.delete();throw e;}
        finally{File[] files=stage.listFiles();if(files!=null)for(File f:files)f.delete();stage.delete();}
    }
}
