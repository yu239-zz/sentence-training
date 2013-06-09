(#(PSI
    APPROACHED
    1
    #F
    2
    #(#((DISCRETE 0.5 0.49 0.01 0. 0.)
         (DISCRETE 0.25 0.25 0.25 0.25)
         (DISCRETE 0.5 0.49 0.01 0. 0.)
         (DISCRETE 0.25 0.25 0.25 0.25)
         (DISCRETE 0. 0. 1.)
         (DISCRETE 0.5 0.5))
       #((DISCRETE 1e-30 0.1 0.3 0.3 0.3)
          (DISCRETE 0.5 0. 0.5 0.)
          (DISCRETE 0.5 0.49 0.01 0. 0.)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 0.3333333333333333 0.3333333333333333 0.3333333333333333)
          (DISCRETE 0.5 0.5))
       #((DISCRETE 0.5 0.49 0.01 0. 0.)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 0.5 0.49 0.01 0. 0.)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 1. 0. 0.)
          (DISCRETE 0.5 0.5)))
    #(#(0.001 0.999 0.) #(0. 0.99 0.01) #(0. 0. 1.))
    #(1. 0. 0.))
  #(PSI
     PUT-DOWN
     1
     #F
     2
     #(#((DISCRETE 0.2 0.2 0.2 0.2 0.2)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 0.2 0.2 0.2 0.2 0.2)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 1. 0. 0.)
          (DISCRETE 1. 0.))
        #((DISCRETE 0.2 0.2 0.2 0.2 0.2)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 1e-30 0.1 0.3 0.3 0.3)
           (DISCRETE 0. 1. 0. 0.)
           (DISCRETE 1. 0. 0.)
           (DISCRETE 0.5 0.5))
        #((DISCRETE 0.2 0.2 0.2 0.2 0.2)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 0.5 0.49 0.01 0. 0.)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 1. 0. 0.)
           (DISCRETE 1. 0.)))
     #(#(0.001 0.999 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI
     PICKED-UP
     1
     #F
     2
     #(#((DISCRETE 0.2 0.2 0.2 0.2 0.2)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 0.5 0.49 0.01 0. 0.)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 1. 0. 0.)
          (DISCRETE 1. 0.))
        #((DISCRETE 0.2 0.2 0.2 0.2 0.2)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 1e-30 0.1 0.3 0.3 0.3)
           (DISCRETE 0. 0. 0. 1.)
           (DISCRETE 1. 0. 0.)
           (DISCRETE 0.5 0.5))
        #((DISCRETE 0.2 0.2 0.2 0.2 0.2)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 0.5 0.49 0.01 0. 0.)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 1. 0. 0.)
           (DISCRETE 1. 0.)))
     #(#(0.001 0.999 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI
     CARRIED
     1
     #F
     2
     #(#((DISCRETE 0.5 0.49 0.01 0. 0.)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 0.5 0.49 0.01 0. 0.)
          (DISCRETE 0.25 0.25 0.25 0.25)
          (DISCRETE 1. 0. 0.)
          (DISCRETE 1. 0.))
        #((DISCRETE 1e-30 0.1 0.3 0.3 0.3)
           (DISCRETE 0.5 0. 0.5 0.)
           (DISCRETE 1e-30 0.1 0.3 0.3 0.3)
           (DISCRETE 0.5 0. 0.5 0.)
           (DISCRETE 1. 0. 0.)
           (DISCRETE 1. 0.))
        #((DISCRETE 0.5 0.49 0.01 0. 0.)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 0.5 0.49 0.01 0. 0.)
           (DISCRETE 0.25 0.25 0.25 0.25)
           (DISCRETE 1. 0. 0.)
           (DISCRETE 1. 0.)))
     #(#(0.001 0.999 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI PERSON 0 #F 1 #(#((DISCRETE 1. 0. 0. 0.))) #(#(1.)) #(1.))
  #(PSI CHAIR 0 #F 1 #(#((DISCRETE 0. 0. 1. 0.))) #(#(1.)) #(1.))
  #(PSI BACKPACK 0 #F 1 #(#((DISCRETE 0. 1. 0. 0.))) #(#(1.)) #(1.))
  #(PSI TRASH-CAN 0 #F 1 #(#((DISCRETE 0. 0. 0. 1.))) #(#(1.)) #(1.))
  #(PSI OBJECT 0 #F 1 #(#((DISCRETE 0.25 0.25 0.25 0.25))) #(#(1.)) #(1.))
  #(PSI
     QUICKLY
     2
     #F
     1
     #(#((DISCRETE 0.25 0.25 0.25 0.25 1e-30))
        #((DISCRETE 0. 0. 0. 0. 1.))
        #((DISCRETE 0.25 0.25 0.25 0.25 1e-30)))
     #(#(0.01 0.99 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI
     SLOWLY
     2
     #F
     1
     #(#((DISCRETE 0.5 0.49 0.01 0. 0.))
        #((DISCRETE 0. 0.3 0.4 0.3 0.))
        #((DISCRETE 0.5 0.49 0.01 0. 0.)))
     #(#(0.01 0.99 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI TO-THE-LEFT-OF 4 #F 2 #(#((DISCRETE 1. 0. 0.))) #(#(1.)) #(1.))
  #(PSI TO-THE-RIGHT-OF 4 #F 2 #(#((DISCRETE 0. 1. 0.))) #(#(1.)) #(1.))
  #(PSI
     AWAY-FROM
     5
     #F
     2
     #(#((DISCRETE 0.5 0.49 0.01 0. 0.) (DISCRETE 1. 0. 0.))
        #((DISCRETE 1e-30 0.1 0.3 0.3 0.3)
           (DISCRETE 0.3333333333333333 0.3333333333333333 0.3333333333333333))
        #((DISCRETE 0.5 0.49 0.01 0. 0.) (DISCRETE 0. 0. 1.)))
     #(#(0.01 0.99 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI
     TOWARDS
     5
     #F
     2
     #(#((DISCRETE 0.5 0.49 0.01 0. 0.) (DISCRETE 0. 0. 1.))
        #((DISCRETE 1e-30 0.1 0.3 0.3 0.3)
           (DISCRETE 0.3333333333333333 0.3333333333333333 0.3333333333333333))
        #((DISCRETE 0.5 0.49 0.01 0. 0.) (DISCRETE 1. 0. 0.)))
     #(#(0.01 0.99 0.) #(0. 0.99 0.01) #(0. 0. 1.))
     #(1. 0. 0.))
  #(PSI RED 3 #F 1 #(#((DISCRETE 1. 0. 0. 0. 0.))) #(#(1.)) #(1.))
  #(PSI BLUE 3 #F 1 #(#((DISCRETE 0. 0. 0. 0. 1.))) #(#(1.)) #(1.))
  #(PSI GREEN 3 #F 1 #(#((DISCRETE 0. 1. 0. 0. 0.))) #(#(1.)) #(1.))
  #(PSI ORANGE 3 #F 1 #(#((DISCRETE 0. 0. 0. 1. 0.))) #(#(1.)) #(1.))
  #(PSI YELLOW 3 #F 1 #(#((DISCRETE 0. 0. 1. 0. 0.))) #(#(1.)) #(1.)))
