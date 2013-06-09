function an = readannot(annotfile, use3dbox)

data = load(annotfile);
annotation = data.annotation;
nobjects = length(annotation.class);
for j=1:nobjects, if ~numel(annotation.class{j}), annotation.class{j} = ''; end; end;
an.class = annotation.class;
an.bboxes = annotation.bboxes;

if use3dbox
   for j=1:nobjects 
      basebox = annotation.basebox{j}; 
      if numel(basebox) & ~all(basebox==0)
         bbox = get2dfrom3dbox(basebox, annotation.imsize);
         an.bboxes(j, :) = bbox;
      end;
   end;
end;

an.difficult = zeros(nobjects, 1);
for j=1:nobjects
   if numel(annotation.difficult{j}) & isnumeric(annotation.difficult{j})
      an.difficult(j) = annotation.difficult{j};
   else
       diff = lower(annotation.difficult{j});
       if strcmp(diff, 'yes') | strcmp(diff, 'y')
           an.difficult(j) = 1;
       end;
   end;
end;