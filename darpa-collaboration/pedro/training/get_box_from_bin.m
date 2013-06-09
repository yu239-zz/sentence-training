 function box = get_box_from_bin(bin_im)

 l=1;
 r=1;
 t=1;
 b=1;
     for i = 1: size(bin_im,1)
       if(size(find( bin_im(i,:) ~= 0 ),2)~=0)
          t = i;
          break;
      end
     end
     for i = size(bin_im,1) : -1 : 1
      if(size(find( bin_im(i,:) ~= 0 ),2)~=0)
          b= i;
          break;
      end
     end
     for i = 1: size(bin_im,2)
       if(size(find( bin_im(:,i) ~= 0 ),1)~=0)
           l= i;
           break;
       end
     end

     for i = size(bin_im,2) : -1 : 1
       if(size(find( bin_im(:,i) ~= 0 ),1)~=0)
           r= i;
           break;
       end
     end
 box = [l t r b];

 end