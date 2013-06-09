function boxes = predictboxes(dirname, videoname, boxes, method, lk_ahead)
% Method - 0: standard optical flow
%          1: KLT tracker
    num_frames = length(boxes);

    for i=num_frames-1 : -1 :1
        fprintf('i: %03d\n',i);
        [t_0, l_0, b_0, r_0, filter, conf, offset, model_num] = read_single_frame_boxes(boxes,i);
        switch method
          case 0
            l=ceil(t_0/2);
            t=ceil(l_0/2);
            r=ceil(b_0/2);
            b=ceil(r_0/2);
          case 1
            l = t_0;
            t = l_0;
            r = b_0;
            b = r_0;
          otherwise
            disp('Method not handled');
        end
        
        for i_box = 1: size(t_0,1)
            l=l(i_box,1);
            t=t(i_box,1);
            r=r(i_box,1);
            b=b(i_box,1);
            conf_box = conf(i_box,1);
            filter_box=filter(i_box,1);
            offset_box=offset(i_box,1);
            model_num_box=model_num(i_box,1);
            
            for j = i+1 : min(i+lk_ahead,num_frames)
                f_nooverlap = 0;
                switch method
                  case 0
                    opt_file = dlmread([sprintf('%s%s/%04d/optical-flow.ssv',dirname,videosname,j-1)]);
                    opt_hor = opt_file(1:(size(opt_file,1)/2));
                    opt_ver = opt_file((size(opt_file,1)/2)+1:size(opt_file,1));
                    
                    opt_hor_aver =  average_optical_flow_per_box(opt_hor,t(i_box,1),l(i_box,1),b(i_box,1),r(i_box,1));
                    opt_ver_aver = average_optical_flow_per_box(opt_ver,t(i_box,1),l(i_box,1),b(i_box,1),r(i_box,1));
                    conf_box = conf(i_box,1)-0.1;
                    filter_box = filter(i_box,1);
                    predicted_box = [(l(i_box,1)+opt_hor_aver)*2 ...
                                     (t(i_box,1)+opt_ver_aver)*2 ...
                                     (r(i_box,1)+opt_hor_aver)*2 ...
                                     (b(i_box,1)+opt_ver_aver)*2 ...
                                     filter_box conf_box j-i model_num_box];
                  case 1
                    box = [l t r b];
                    [scale_x,  shift_x, scale_y,shift_y] = get_scale_and_shift(dirname, videoname, box, j-1);
                    predicted_box = [scale_x*l+ shift_x  scale_y*t+ ...
                                     shift_y scale_x*r+ shift_x ...
                                     scale_y*b+ shift_y  filter_box ...
                                     conf_box j-i model_num_box];
                  otherwise
                    disp('Method not handled');
                end

                [t_1, l_1, b_1, r_1] = read_single_frame_boxes(boxes,j);
                for i_tmp = 1: size(t_1,1)
                    if(t_1(i_tmp,1) > predicted_box(1,3) || ...
                       b_1(i_tmp) < predicted_box(1,1) || ...
                       l_1(i_tmp,1) >  predicted_box(1,4) ...
                       || r_1(i_tmp,1) < predicted_box(1,2) )
                        f_nooverlap = 1;
                    else
                        l1=[t_1(i_tmp,1) b_1(i_tmp,1) predicted_box(1,1) predicted_box(1,3)];
                        l2=[l_1(i_tmp,1) r_1(i_tmp,1) predicted_box(1,2) predicted_box(1,4)];
                        [l1_sorted, l1_index] = sort(l1);
                        [l2_sorted, l2_index] = sort(l2);
                        area_intersect = abs((l1_sorted(2)-l1_sorted(3))*(l2_sorted(2)-l2_sorted(3)));
                        area_union = abs((r_1(i_tmp,1) - ...
                                          l_1(i_tmp,1))* ...
                                         (b_1(i_tmp,1)- ...
                                          t_1(i_tmp,1))) + ...
                            abs((predicted_box(1,3)- ...
                                 predicted_box(1,1))* ...
                                (predicted_box(1,4)- ...
                                 predicted_box(1,2))) - area_intersect;
                        if(area_intersect/area_union < 0.1) %overlap threshold can be changed
                            f_nooverlap = 1;
                        else
                            f_nooverlap =1;
                            break;
                        end
                    end
                end

                if(f_nooverlap==1)
                    boxes{j} = [boxes{j}; ...
                                predicted_box(1,1), predicted_box(1,2), ...
                                predicted_box(1,3),predicted_box(1,4), ...
                                predicted_box(1,5),predicted_box(1,6), ...
                                predicted_box(1,7),predicted_box(1,8)];
                    switch method
                      case 0
                        l=ceil(predicted_box(1,1)/2);
                        t=ceil(predicted_box(1,2)/2);
                        r=ceil(predicted_box(1,3)/2);
                        b=ceil(predicted_box(1,4)/2);
                      case 1 
                        l=predicted_box(1,1);
                        t=predicted_box(1,2);
                        r=predicted_box(1,3);
                        b=predicted_box(1,4);
                        filter_box=predicted_box(1,5);
                        conf_box=predicted_box(1,6);
                        offset_box=predicted_box(1,7);
                        model_num_box=predicted_box(1,8);
                      otherwise
                        disp('Method not handled');
                    end
                    f_nooverlap = 0;
                else
                    switch method
                      case 0
                        l=ceil(t_0/2);
                        t=ceil(l_0/2);
                        r=ceil(b_0/2);
                        b=ceil(r_0/2);
                      case 1
                        l=t_0;
                        t=l_0;
                        r=b_0;
                        b=r_0;
                      otherwise
                        disp('Method not handled');
                    end
                    break;
                end
            end
            switch method
              case 0
                l=ceil(t_0/2);
                t=ceil(l_0/2);
                r=ceil(b_0/2);
                b=ceil(r_0/2);
              case 1
                l=t_0;
                t=l_0;
                r=b_0;
                b=r_0;
              otherwise
                disp('Method not handled');
            end
        end 
    end
    fprintf('\n');
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
