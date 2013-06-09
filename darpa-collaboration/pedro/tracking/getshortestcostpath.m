function [path, start, finish] = getshortestcostpath(dirname, videoname, boxes, threshold)

    info_frame = struct([]);
    % drop initial false tracks
    for start=1:length(boxes)
        [t_0,l_0,b_0,r_0,filter,conf,offset,model_num] = read_single_frame_boxes(boxes, start);
        if (max(conf) >= threshold)
            break;
        else
            info_frame{start}.flag =0;
            info_frame{start}.box(1,:) = [-1 -1 -1 -1];
            info_frame{start}.conf(1,1) = -intmax; % default 32 bit Int
            info_frame{start}.filter(1,1) = -1;
            info_frame{start}.offset(1,1) = 0;
            info_frame{start}.model_num(1,1) = -1;
        end
    end

    % drop final false tracks    
    for finish=length(boxes):-1:1
        [t_0,l_0,b_0,r_0,filter,conf,offset,model_num] = read_single_frame_boxes(boxes, finish);
        if (max(conf) >= threshold)
            break;
        else
            info_frame{finish}.flag =0;
            info_frame{finish}.box(1,:) = [-1 -1 -1 -1];
            info_frame{finish}.conf(1,1) = -intmax; % default 32 bit Int
            info_frame{finish}.filter(1,1) = -1;
            info_frame{finish}.offset(1,1) = 0;
            info_frame{finish}.model_num(1,1) = -1;
        end
    end

    fprintf('Tracking for [%03d %03d] of [%03d %03d]\n',start,finish,1,length(boxes));

    % check for invalid start and end positions
    if (start > finish)
        fprintf(2,'Model drop threshold too high - entire video dropped\n');
        path = info_frame;
        start = -1;
        finish = -1;
        return;
    end
        
    for i=start:finish
        fprintf('i: %03d\n',i);
        [t_0,l_0,b_0,r_0,filter,conf,offset,model_num] = read_single_frame_boxes(boxes,i);
	if (length(conf) == 0)
            [t_0,l_0,b_0,r_0,filter,conf,offset,model_num] = read_single_frame_boxes(boxes,last_legal_index);
        else
            last_legal_index = i;
        end
        info_frame{i}.flag =1;
        if ((start == finish) && (finish == length(boxes))) || ...
                    ((finish-start)<10) %debgging
                                                              
            start = -1;
            break;
        end
        if(i < length(boxes))
            opt_file = dlmread([sprintf('%s/%s/%04d/optical-flow.ssv',dirname,videoname,i)]);
            opt_hor = opt_file(1:(size(opt_file,1)/2),:);
            opt_ver = opt_file((size(opt_file,1)/2)+1:size(opt_file,1),:);
        end
        % x y exchanged
        l=ceil(t_0/2);
        t=ceil(l_0/2);
        r=floor(b_0/2);
        b=floor(r_0/2);
        for jj=1:size(l,1)
            if(l(jj)<=0)
                l(jj) =1;
            end
            if(t(jj)<=0)
                t(jj)=1;
            end
            if(r(jj)>size(opt_hor,2))
                r(jj) = size(opt_hor,2);
            end
            if(b(jj)>size(opt_hor,1))
                b(jj)=size(opt_hor,1);
            end
        end

        for i_box =1 :size(t,1)
            info_frame{i}.box(i_box,:) = [t(i_box,1)*2 l(i_box,1)*2 b(i_box,1)*2 r(i_box,1)*2];
            info_frame{i}.center(i_box,:) = [(t(i_box,1)+b(i_box,1))/2 (l(i_box,1)+r(i_box,1))/2];
            info_frame{i}.w(i_box,:) = (r(i_box,1)-l(i_box,1))*2;
            info_frame{i}.h(i_box,:) = (b(i_box,1)-t(i_box,1))*2;
            info_frame{i}.conf(i_box,1) = conf(i_box,1);
            info_frame{i}.filter(i_box,1) = filter(i_box,1);
            info_frame{i}.offset(i_box,1) = offset(i_box,1);
            info_frame{i}.model_num(i_box,1) = model_num(i_box,1);
            if(i < length(boxes))
                info_frame{i}.opt_hor_ave(i_box) = ...
                    average_optical_flow_per_box(opt_hor,t(i_box,1), l(i_box,1), b(i_box,1), r(i_box,1));
                info_frame{i}.opt_ver_ave(i_box) = ...
                    average_optical_flow_per_box(opt_ver,t(i_box,1), l(i_box,1), b(i_box,1), r(i_box,1));
                info_frame{i}.opt_center(i_box,:) = ...
                    [info_frame{i}.center(i_box,1)+ ...
                     info_frame{i}.opt_ver_ave(i_box) ...
                     info_frame{i}.center(i_box,2)+ ...
                     info_frame{i}.opt_hor_ave(i_box)];
            else
                info_frame{i}.opt_hor_ave(i_box) = 0;
                info_frame{i}.opt_ver_ave(i_box) = 0;
                info_frame{i}.opt_center(i_box,:) = [0 0];
            end
            [info_frame{i}.minDistance_optC2boxC(i_box), ...
             info_frame{i}.minDistance_path(i_box)] = ...
                findmindisfromfirstframe(info_frame,i,i_box,start); % 1->start
            info_frame{i}.minDistance_optC2boxC(i_box) = ...
                info_frame{i}.minDistance_optC2boxC(i_box) - ...
                info_frame{i}.conf(i_box,1)*10;
        end
    end
    fprintf('\n');
    path = info_frame;
end

function aver= average_optical_flow_per_box(opt,t,l,b,r)
    sum_opt = 0;
    for i = t:b
        for j=l:r
            sum_opt = sum_opt+ opt(i,j);
        end
    end
    aver = sum_opt/((b-t)*(r-l));
end

function [mindis, path_preboxnum] = findmindisfromfirstframe(info_frame,i_frame,i_box,firstframe)
    if(i_frame ==firstframe)
        mindis = 0;
        path_preboxnum =0;
        return;
    end
    mindis = 1000000;
    % check for the only viterbi crash location/condition:
    if (~isfield(info_frame{i_frame-1},'minDistance_optC2boxC'))
      fprintf(stederr,'\nFrame %04d does not have a minDistance_optC2boxC\n',i_frame-1);
    end
    for i = 1: length(info_frame{i_frame-1}.minDistance_optC2boxC)
        if(dis_ptop(info_frame{i_frame-1}.opt_center(i,:), ...
                    info_frame{i_frame}.center(i_box,:)) + ...
           info_frame{i_frame-1}.minDistance_optC2boxC(i) < mindis)
            mindis = dis_ptop(info_frame{i_frame-1}.opt_center(i,:), ...
                              info_frame{i_frame}.center(i_box,:)) ...
                     + info_frame{i_frame-1}.minDistance_optC2boxC(i);
            path_preboxnum = i;
        end
    end
end

function d = dis_ptop(p1,p2)
    d  = sqrt((p1(1,1)-p2(1,1))^2+(p1(1,2)-p2(1,2))^2);
end

function [t,l,b,r,f,c,offset,model_num] = read_single_frame_boxes(boxes, index)
    single_box = boxes{index};
    t = single_box(:,1);
    l = single_box(:,2);
    b = single_box(:,3);
    r = single_box(:,4);
    f = single_box(:,5);
    c = single_box(:,6);
    offset = single_box(:,7);
    model_num = single_box(:,8);
end
