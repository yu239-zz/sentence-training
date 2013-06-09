function [kx, bx, ky, by] = get_scale_and_shift(dirname, videoname, box, frame)
    [x_1, y_1, feature_1] = klt_read_featuretable_oneframe(sprintf('%s/%s/%04d/klt-current.txt',dirname,videoname,frame));
    [x_2, y_2, feature_2] = klt_read_featuretable_oneframe(sprintf('%s/%s/%04d/klt-next.txt',dirname,videoname,frame));
    selected_features = zeros(0,3);
    i_count = 1;
    x_1_selected = [];
    x_2_selected = [];
    y_1_selected = [];
    y_2_selected = [];
    for i = 1 :  size( x_2, 1 )
        if( x_1( i, 1 ) > box(1,1) & x_1( i, 1 ) < box( 1, 3) & y_1( ...
            i, 1 ) >  box(1,2) & y_1(i,1) < box(1,4) & feature_2(i,1) == 0)
            % if(abs(x_1(i,1) - x_2(i,1)) > 0 | abs(y_1(i,1) - y_2(i,1)) > 0  )
            x_1_selected(i_count) = x_1(i,1);
            x_2_selected(i_count) = x_2(i,1);
            y_1_selected(i_count) = y_1(i,1);
            y_2_selected(i_count) = y_2(i,1);
            % selected_features(i_count, :) = [x_2(i,1) y_2(i,1) feature_2(i,1)];
            i_count = i_count + 1;
            % end
        end
    end
    if(i_count-1 <2)
        kx =1;
        bx =0;
        ky = 1;
        by = 0;
    else
        num_rm = round(0.1*size(x_1_selected,2));
        ax = polyfit(x_1_selected, x_2_selected,1);
        ay = polyfit(y_1_selected, y_2_selected,1);
        if (ax(1)<0|ay(1)<0) || (ax(1)>1000 || ax(2)>1000)
            kx =1;
            bx=0;
            ky=1;
            by=0;
            % display('no fit, return!');
            return;
        end
        if(i_count > 10)
            x_err = x_2_selected - polyval(ax, x_1_selected);
            y_err = y_2_selected - polyval(ay, y_1_selected);

            [x_err_sorted, x_index] = sort(abs(x_err));
            [y_err_sorted, y_index] = sort(abs(y_err));

            ax = polyfit(x_1_selected(x_index(1:end-num_rm)), x_2_selected(x_index(1:end-num_rm)),1);
            ay = polyfit(y_1_selected(y_index(1:end-num_rm)), y_2_selected(y_index(1:end-num_rm)),1);

            x_err = x_2_selected(x_index(1:end-num_rm)) - polyval(ax, x_1_selected(x_index(1:end-num_rm)));
            y_err = y_2_selected(y_index(1:end-num_rm)) - polyval(ay, y_1_selected(y_index(1:end-num_rm)));
        end
        kx = ax(1);
        bx = ax(2);
        ky = ay(1);
        by = ay(2);
        if(isnan(kx)|isnan(bx)|isnan(ky)|isnan(by)|isinf(kx)|isinf(bx)|isinf(ky)|isinf(by))
            kx=1;
            ky=1;
            bx=0;
            by=0;
        end
    end
end
