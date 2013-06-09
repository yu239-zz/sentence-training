function res = export_pedro_model_to_scheme(model, out_filename)

%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%  Check Input Params   %%
%%%%%%%%%%%%%%%%%%%%%%%%%%%

% Assume failure
res = 0;

% set loadres to "null"
%loadres = libpointer; 

if nargin < 2
    fprintf(2, 'Must specify both a model and a filename to save to');
    return;
end

if nargin < 1
   fprintf(2, 'Must specify the filename of a model file\n');
   return;
end

% Open the file for output
fid = fopen(out_filename, 'w');
if fid == -1
    fprintf(2, 'Failed to open file for writing: %s', out_filename);
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% Start of model output %%
%%%%%%%%%%%%%%%%%%%%%%%%%%%

fprintf(fid, '#(PEDRO-MODEL\n');
tab = 1;
print_field(fid, tab, ['"' model.class '"'], 'class');
print_field(fid, tab, ['"' model.year '"'], 'year');
print_field(fid, tab, ['"' model.note '"'], 'note');
print_field(fid, tab, model.numfilters, 'numfilters');
print_field(fid, tab, model.numblocks, 'numblocks');
print_field(fid, tab, model.numsymbols, 'numsymbols');
print_field(fid, tab, model.start, 'start');
print_field(fid, tab, sprintf('#(%d %d)', model.maxsize(1), model.maxsize(2)), 'maxsize');
print_field(fid, tab, sprintf('#(%d %d)', model.minsize(1), model.minsize(2)), 'minsize');
print_field(fid, tab, model.interval, 'interval');
print_field(fid, tab, model.sbin, 'sbin');
print_field(fid, tab, model.thresh, 'thresh');

if isfield(model, 'pyra_pad') == 1
    print_field(fid, tab, sprintf('#(%d %d)', model.pyra_pad(1), model.pyra_pad(2)), 'pyra_pad');
else
    print_field(fid, tab, sprintf('#(%d %d)', model.maxsize(1), model.maxsize(2)), 'pyra_pad');
end

if isfield(model, 'pyra_scales') == 1
    print_array1(fid, tab, model.pyra_scales, 1);
else
    print_field(fid, tab, '#()', 'pyra_scales');
end



%% model.filters %%
fprintf(fid, ['%s( ;; PEDRO-FILTER: blocklabel, symmetric, size(y,x), flip, ' ...
              'symbol, (y,x,32 matrix of numbers)\n'], tabs(tab));
tab = tab + 1;
for n = 1:model.numfilters
    filter = model.filters(n);
    fprintf(fid, '%s#(PEDRO-FILTER %d "%s" #(%d %d) %d %d\n', ...    
                tabs(tab), filter.blocklabel, filter.symmetric, filter.size(1), ...
                filter.size(2), filter.flip, filter.symbol);
    
    % Output the 'w'
    print_array3(fid, tab + 1, filter.w);
    
    fprintf(fid, '%s) ; End PEDRO-FILTER #%d\n', tabs(tab), n);
end
tab = tab - 1;
fprintf(fid, '%s) ; end filters\n', tabs(tab));

%% mode.rules %%
fprintf(fid, '%s( ;; Each symbol can have 0 or more PEDRO-RULE\n', tabs(tab));
fprintf(fid, ['%s  ;; PEDRO-RULE: type, lhs, rhs, detwindow, i, ' ...
              '(offset), (anchors), (def), #(scores)\n'], tabs(tab));
fprintf(fid, '%s  ;; (anchors) is a list tuples.\n', tabs(tab));
fprintf(fid, '%s  ;; (def) as list of PEDRO-RULE-DEF: w, blocklabel, flip, symmetric\n', tabs(tab));
tab = tab + 1;
for n = 1:model.numsymbols
   dims = size(model.rules{1,n});
   n_rules = dims(2);
   fprintf(fid, '%s(', tabs(tab)); % start the rule-list
   for r = 1:n_rules
       indent = [tabs(tab) ' '];
       if r == 1
           indent = '';
       end
       print_rule(fid, indent, model.rules{1,n}(r), r ~= n_rules, n == model.start);
   end
   fprintf(fid, ')\n'); % end the rule-list
end
tab = tab - 1;
fprintf(fid, '%s) ; end rules\n', tabs(tab));

%% model.symbols %%
fprintf(fid, '%s( ;; PEDRO-SYMBOL: type, i, filter, score\n', tabs(tab));
tab = tab + 1;
for n = 1:model.numsymbols
   symbol = model.symbols(n);
   if isempty(symbol.filter)
       filter_str = '()';
   else
       filter_str = sprintf('%d', symbol.filter);
   end
   fprintf(fid, '%s#(PEDRO-SYMBOL "%s" %d %s', tabs(tab), symbol.type, ...
           symbol.i, filter_str);
       
   if n == model.start && isfield(symbol, 'score') == 1
       fprintf(fid, '\n');
       print_cell_array2(fid, tab + 1, symbol.score);
   else
       fprintf(fid, ' #()');
   end
       
   fprintf(fid, ')\n');
end
tab = tab - 1;
fprintf(fid, '%s) ; end symbols\n', tabs(tab));

%% model.blocksizes %%
fprintf(fid, '%s;; blocksizes\n', tabs(tab));
print_array1(fid, tab, model.blocksizes);

%% model.regmult %%
fprintf(fid, '%s;; regmult\n', tabs(tab));
print_array1(fid, tab, model.regmult);

%% model.learnmult %%
fprintf(fid, '%s;; learnmult\n', tabs(tab));
print_array1(fid, tab, model.learnmult);

%% model.lowerbounds %%
fprintf(fid, '%s( ; lowerbounds: a list of number vectors\n', tabs(tab));
tab = tab + 1;
n_lowerbounds = size(model.lowerbounds);
for n = 1:n_lowerbounds(2)
    print_array1(fid, tab, model.lowerbounds{n});
end
tab = tab - 1;
fprintf(fid, '%s) ; end lowerbounds\n', tabs(tab));

%% model.fusage %%
fprintf(fid, '%s;; fusage\n', tabs(tab));
print_array1(fid, tab, model.fusage);

%% model.bboxpred %%
fprintf(fid, '%s( ;; PEDRO-BBOX: x1 y1 x2 y2, where each is an array\n', tabs(tab));
tab = tab + 1;
if isfield(model, 'bboxpred')
    bboxpred_size = size(model.bboxpred);
    for n = 1:bboxpred_size(1)
        fprintf(fid, '%s(PEDRO-BBOX ', tabs(tab));
        print_array1(fid, 0    , model.bboxpred{n,1}.x1);
        print_array1(fid, tab+4, model.bboxpred{n,1}.y1);
        print_array1(fid, tab+4, model.bboxpred{n,1}.x2);
        print_array1(fid, tab+4, model.bboxpred{n,1}.y2, 0);
        fprintf(fid, ')\n');
    end
end
tab = tab - 1;
fprintf(fid, '%s) ; end bboxpred\n', tabs(tab));

%% model.scoretpt %%
fprintf(fid, '%s ;; 3 dimensional "sparse" array of doubles\n', tabs(tab));
if isfield(model, 'scoretpt')
    % scoretpt is /almost/ and array, but uses {1}(1,1), instead of (1,1,1)
    % to access the i-j-kth element. We convert scoretpt to an array here
    print_cell_array2(fid, tab + 1, model.scoretpt);
else
    fprintf(fid, '%s#()', tabs(tab + 1));
end
fprintf(fid, '%s ;; end scoretpt\n', tabs(tab));


%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%  End of model output  %%
%%%%%%%%%%%%%%%%%%%%%%%%%%%

fprintf(fid, ') ; End PEDRO-MODEL\n');

fclose(fid);

res = 1; % success!

return;


%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%   Helper Functions    %%
%%%%%%%%%%%%%%%%%%%%%%%%%%%

function res = tabs(n)
   res = '';
   for i = 1:n
       res = [ res '   ' ];
   end

function print_field(fid, tab, field, comment)
   format = '%s%-25s ; %s\n';
   if isnumeric(field) == 1
       if floor(field) == field
          format = '%s%-25d ; %s\n';
       else
          format = '%s%-25f ; %s\n';
       end
   end
   fprintf(fid, format, tabs(tab), field, comment); 

function print_array1(fid, tab, arr, print_newline)
   if nargin < 4
       print_newline = 1;
   end
   if isnumeric(tab)
       tab = tabs(tab);
   end
   dims = size(arr);
   len = dims(2);
   if len == 1 && dims(1) > 1
       len = dims(1);
   end
   fprintf(fid, '%s#(', tab);
   for i = 1:len
       n = arr(i);
       if floor(n) == n
           fprintf(fid, '%d', n);
       else
           fprintf(fid, '%f', n);
       end
       if i ~= len
           fprintf(fid, ' ');
       end
   end
   fprintf(fid, ')');
   if print_newline == 1
       fprintf(fid, '\n');
   end
   
function print_array3(fid, tab, arr)
   tab2 = [ tabs(tab) '  ' ];
   tab3 = [ tabs(tab) '    ' ];
   dims = size(arr);
   fprintf(fid, '%s#(', tabs(tab));
   for i = 1:dims(1)
       if i == 1 
           fprintf(fid, '#('); 
       else
           fprintf(fid, '%s#(', tab2);              
       end
       for j = 1:dims(2)
           if j == 1 
               fprintf(fid, '#('); 
           else
               fprintf(fid, '%s#(', tab3);              
           end
           for k = 1:dims(3)
               n = arr(i, j, k);
               format = '%f ';
               if floor(n) == n
                   format = '%d ';
               end
               fprintf(fid, format, n);               
           end
           if j < dims(2) 
               fprintf(fid, ')\n'); 
           else
               fprintf(fid, ')');
           end          
       end
       if i < dims(1) 
           fprintf(fid, ')\n'); 
       else
           fprintf(fid, ')');
       end
   end
   fprintf(fid, ')\n');
   
function print_cell_array2(fid, tab, arr)
    tab2 = [ tabs(tab) '  ' ];
    tab3 = [ tabs(tab) '    ' ];    
    fprintf(fid, '%s#(', tabs(tab));
    sz = size(arr);
    for i = 1:sz(2)
        scores = arr{i};
        dims = size(scores);
        if i == 1 
           fprintf(fid, '#('); 
        else
           fprintf(fid, '%s#(', tab2);              
        end
        for j = 1:dims(1)
            if j == 1 
               fprintf(fid, '#('); 
            else
               fprintf(fid, '%s#(', tab3);              
            end
            for k = 1:dims(2)
                n = scores(j, k);
                format = '%f ';
                if floor(n) == n
                   format = '%d ';
                end
                fprintf(fid, format, n);    
            end
            if j < dims(1) 
               fprintf(fid, ')\n'); 
            else
               fprintf(fid, ')');
            end 
        end
        if i < sz(2) 
           fprintf(fid, ')\n'); 
        else
           fprintf(fid, ')');
        end
    end
    fprintf(fid, ')\n');


function print_rule(fid, indent, rule, print_newline, print_scores)
   fprintf(fid, '%s#(PEDRO-RULE "%s" %d ', indent, rule.type, rule.lhs);
   print_array1(fid, '', rule.rhs, 0);
   fprintf(fid, ' #(%d %d) %d ', rule.detwindow(1), rule.detwindow(2), rule.i);
   
   % now a list of offsets
   n_offsets = size(rule.offset);
   fprintf(fid, '(');
   for i = 1:n_offsets(2)
      fprintf(fid, '#(PEDRO-RULE-OFFSET %f %d)', ...
              rule.offset(i).w, rule.offset(i).blocklabel);
      if i ~= n_offsets(2)
          fprintf(fid, ' ');
      end
   end
   fprintf(fid, ')');
   
   % now the anchors
   fprintf(fid, ' (');
   if isfield(rule, 'anchor')
      n_anchors = size(rule.anchor);
      for i = 1:n_anchors(2)
         print_array1(fid, '', rule.anchor{1,i}, 0);
         if i ~= n_anchors(2)
             fprintf(fid, ' ');
         end
      end
   end
   fprintf(fid, ')');
   
   % now the defs
   fprintf(fid, ' (');
   if isfield(rule, 'def')
      n_defs = size(rule.def);
      for i = 1:n_defs(2)
         fprintf(fid, '#(PEDRO-RULE-DEF ');
         print_array1(fid, '', rule.def(i).w, 0);
         fprintf(fid, ' %d %d "%s"', rule.def(i).blocklabel, ...
                 rule.def(i).flip, rule.def(i).symmetric);
         fprintf(fid, ')');
         if i ~= n_defs(2)
             fprintf(fid, ' ');
         end
      end
   end
   fprintf(fid, ')');
   
   % now the scores (if they exist)
   fprintf(fid, ' ');
   if print_scores == 1 && isfield(rule, 'score')
       scores = rule.score;
       print_cell_array2(fid, 4, scores);
   else
       fprintf(fid, '#()');
   end
   
   
   fprintf(fid, ')');
   if print_newline == 1
       fprintf(fid, '\n');
   end
   



