function box = ip_to_box(rules,l,i,j,v,scale,padding)
    pady=padding(1);
    padx=padding(2);
    found = 0;
    for index=1:length(rules{1})
        if rules{1}(index).score{l}(i-virtpadding(pady,0),j-virtpadding(padx,0)) == v
            found = 1; break;
        end
    end
    if (found == 1)
        detwindow = rules{1}(index).detwindow;
        x1 = (j-padx)*scale;
        y1 = (i-pady)*scale;
        box = [ x1, y1, (x1 + padx*scale), (y1 + pady*scale) ];
    else
        fprintf(2,'Error finding appropriate rule\n');
        box = [];
    end
end

function d = virtpadding(p,ds)
    d = p * (2^ds - 1);
end
