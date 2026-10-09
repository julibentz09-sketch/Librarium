-- Datos iniciales. Se cargan solo la primera vez (cuando la tabla autor está vacía).
-- Para recargarlos después de editar este archivo, borrá librarium.db y volvé a iniciar.

INSERT INTO autor (id, nombre, fecha_nacimiento, lugar_nacimiento, fallecimiento, destacado, biografia, foto, orden) VALUES
('roa-bastos', 'Augusto Roa Bastos', '13 de junio de 1917', 'Asunción, Paraguay', '26 de abril de 2005, Asunción',
 'Premio Cervantes 1989',
 'Novelista y cuentista, es el escritor paraguayo más reconocido en el mundo. Vivió gran parte de su vida exiliado en Argentina y Francia, desde donde escribió sobre la historia, el poder y la identidad del Paraguay. En 1989 recibió el Premio Cervantes, el galardón más importante de la literatura en español.',
 'img/roa-bastos.png', 1),

('delfina-acosta', 'Delfina Acosta', '29 de noviembre de 1956', 'Asunción, Paraguay', NULL,
 'Poetisa y cuentista premiada',
 'Poetisa, cuentista y periodista paraguaya. Su obra, premiada en certámenes nacionales e internacionales, se destaca por su lirismo y por dar voz a la mujer, la maternidad y la vida cotidiana. Es una de las voces más importantes de la poesía paraguaya contemporánea.',
 'img/delfina-acosta.png', 2),

('josefina-pla', 'Josefina Plá', '9 de noviembre de 1903', 'Isla de Lobos, Canarias (España)', '11 de enero de 1999, Asunción',
 'Poetisa, dramaturga y ceramista',
 'Nacida en España, llegó al Paraguay en 1927 tras casarse con el ceramista paraguayo Andrés Campos Cervera y adoptó el país como su patria. Fue poetisa, narradora, dramaturga, periodista, ceramista, crítica de arte e historiadora. Es una de las figuras fundamentales de la cultura paraguaya del siglo XX y una pionera de la literatura escrita por mujeres en el país.',
 'img/josefina-pla.png', 3),

('mauricio-cardozo-ocampo', 'Mauricio Cardozo Ocampo', '13 de septiembre de 1907', 'Ybycuí, Paraguarí (Paraguay)', '5 de agosto de 1982, Buenos Aires',
 'Compositor, poeta y folclorista',
 'Músico, poeta y estudioso del folclore paraguayo. Compuso música y letras en guaraní y castellano que forman parte del cancionero popular, y vivió muchos años en Buenos Aires, donde difundió la música paraguaya. Investigó y escribió sobre las tradiciones, danzas y costumbres de su pueblo.',
 'img/mauricio-cardozo-ocampo.png', 4);

INSERT INTO obra (autor_id, titulo, anio, genero, descripcion, orden) VALUES
('roa-bastos', 'Yo el Supremo', 1974, 'Novela', 'Su obra maestra. Recrea la figura del dictador José Gaspar Rodríguez de Francia y reflexiona sobre el poder absoluto y la escritura.', 1),
('roa-bastos', 'Hijo de hombre', 1960, 'Novela', 'Recorre la historia paraguaya hasta la Guerra del Chaco a través de personajes del pueblo que resisten la injusticia.', 2),
('roa-bastos', 'El trueno entre las hojas', 1953, 'Cuentos', 'Su primer libro de cuentos. Retrata la vida rural paraguaya, la explotación y la lucha de los trabajadores.', 3),

('delfina-acosta', 'Todas las voces, mujer', 1986, 'Poesía', 'Poemario que da voz a las experiencias, los deseos y las luchas de la mujer.', 1),
('delfina-acosta', 'El romancero del hijo', NULL, 'Poesía', 'Poemas en forma de romance dedicados al vínculo entre madre e hijo.', 2),
('delfina-acosta', 'Querido Salvador', NULL, 'Narrativa', NULL, 3),

('josefina-pla', 'El precio de los sueños', 1934, 'Poesía', 'Su primer libro de poemas, uno de los primeros publicados por una mujer en el Paraguay.', 1),
('josefina-pla', 'La raíz y la aurora', 1960, 'Poesía', 'Poemario de madurez sobre el amor, el tiempo y la identidad.', 2),
('josefina-pla', 'La muralla robada', 1989, 'Cuentos', 'Colección de cuentos que retrata personajes y situaciones de la sociedad paraguaya.', 3),

('mauricio-cardozo-ocampo', 'Galopera', NULL, 'Canción (polca)', 'Una de las polcas más conocidas del Paraguay, inspirada en las galoperas que bailan con cántaros en la cabeza.', 1),
('mauricio-cardozo-ocampo', 'Pueblo Ybycuí', NULL, 'Canción', 'Homenaje a su pueblo natal y a la vida del campo paraguayo.', 2),
('mauricio-cardozo-ocampo', 'Mundo folklórico paraguayo', NULL, 'Ensayo', 'Estudio sobre las tradiciones, leyendas, danzas y costumbres populares del Paraguay.', 3);

INSERT INTO multimedia (autor_id, tipo, url, descripcion) VALUES
('roa-bastos', 'imagen', 'img/roa-bastos.png', 'Retrato del autor'),
('roa-bastos', 'audio', NULL, 'Biografía leída con voz sintetizada'),
('delfina-acosta', 'imagen', 'img/delfina-acosta.png', 'Retrato de la autora'),
('delfina-acosta', 'audio', NULL, 'Biografía leída con voz sintetizada'),
('josefina-pla', 'imagen', 'img/josefina-pla.png', 'Retrato de la autora'),
('josefina-pla', 'audio', NULL, 'Biografía leída con voz sintetizada'),
('mauricio-cardozo-ocampo', 'imagen', 'img/mauricio-cardozo-ocampo.png', 'Retrato del autor'),
('mauricio-cardozo-ocampo', 'audio', NULL, 'Biografía leída con voz sintetizada');

INSERT INTO marcador (id, autor_id, imagen) VALUES
('MK-ROA', 'roa-bastos', 'img/roa-bastos.png'),
('MK-DEL', 'delfina-acosta', 'img/delfina-acosta.png'),
('MK-PLA', 'josefina-pla', 'img/josefina-pla.png'),
('MK-MCO', 'mauricio-cardozo-ocampo', 'img/mauricio-cardozo-ocampo.png');
