(function(root){
  'use strict';
  const MAX_FILE=15*1024*1024, MAX_NOTE=40*1024*1024;
  const plain=html=>String(html||'').replace(/<[^>]*>/g,' ').replace(/&nbsp;/g,' ').replace(/\s+/g,' ').trim();
  function validate(note){
    if(!note||typeof note!=='object'||typeof note.id!=='string'||typeof note.html!=='string'||!Array.isArray(note.attachments))throw new Error('La copia contiene una nota no válida.');
    if(note.id.length>100||note.html.length>MAX_NOTE||note.attachments.length>100)throw new Error('La nota supera el tamaño admitido.');
    for(const attachment of note.attachments){if(!attachment||typeof attachment.id!=='string'||typeof attachment.name!=='string'||typeof attachment.type!=='string'||typeof attachment.data!=='string'||!/^data:[\w.+/-]*;base64,[a-z0-9+/=]*$/i.test(attachment.data)||attachment.data.length>MAX_FILE*1.4)throw new Error('Archivo adjunto no válido.');}
    if(JSON.stringify(note).length>MAX_NOTE)throw new Error('La nota supera el límite de 40 MB.');
    const notebook=note.kind==='notebook';
    if(note.coverImage&&(!/^data:image\/(png|jpeg|webp);base64,[a-z0-9+/=]+$/i.test(note.coverImage)||note.coverImage.length>MAX_FILE*1.4))throw new Error('Imagen de portada no válida.');
    const pages=notebook?(note.pages||[{id:'first',name:'Página 1',html:note.html,paper:'plain'}]):[];
    if(!Array.isArray(pages)||pages.length>200||notebook&&!pages.length)throw new Error('El cuaderno debe tener entre 1 y 200 páginas.');
    const normalized=pages.map(p=>{if(!p||typeof p.id!=='string'||typeof p.html!=='string')throw new Error('Página no válida.');return {...p,name:String(p.name||'Página').slice(0,100),paper:['plain','ruled','grid','dots','cornell'].includes(p.paper)?p.paper:'plain'};});
    if(new Set(normalized.map(p=>p.id)).size!==normalized.length)throw new Error('Identificadores de página repetidos.');
    return {...note,kind:notebook?'notebook':'note',pages:normalized,pageIndex:Math.max(0,Math.min(normalized.length-1,Number.isInteger(note.pageIndex)?note.pageIndex:0)),cover:['lavender','sage','rose','night','sand'].includes(note.cover)?note.cover:'lavender',paper:['plain','ruled','grid','dots','cornell'].includes(note.paper)?note.paper:'plain',title:String(note.title||'').slice(0,250),folder:String(note.folder||'').slice(0,150),tags:String(note.tags||'').slice(0,500),favorite:!!note.favorite,trash:!!note.trash,created:Number(note.created)||Date.now(),updated:Number(note.updated)||Date.now()};
  }
  function filterNotes(notes,filter,search,sort){
    const query=String(search||'').toLocaleLowerCase('es');
    return notes.filter(n=>n.trash===(filter==='trash')).filter(n=>filter==='notebooks'?n.kind==='notebook':filter==='favorites'?n.favorite:filter.startsWith('folder:')?n.folder===filter.slice(7)||n.folder.startsWith(filter.slice(7)+'/'):true).filter(n=>(n.title+' '+plain(n.html)+' '+(n.pages||[]).map(p=>p.name+' '+plain(p.html)).join(' ')+' '+n.tags+' '+n.attachments.map(a=>a.name).join(' ')).toLocaleLowerCase('es').includes(query)).sort(sort==='title'?(a,b)=>(a.title||'Sin título').localeCompare(b.title||'Sin título','es'):(a,b)=>b.updated-a.updated);
  }
  function parseBackup(value){if(!value||value.format!=='mis-notas-notebook'||value.version!==1||!Array.isArray(value.notes)||value.notes.length>2000)throw new Error('Selecciona una copia JSON de Mis notas Notebook.');return value.notes.map(validate);}
  const api={MAX_FILE,MAX_NOTE,plain,validate,filterNotes,parseBackup};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.NotesCore=api;
})(typeof globalThis!=='undefined'?globalThis:this);
