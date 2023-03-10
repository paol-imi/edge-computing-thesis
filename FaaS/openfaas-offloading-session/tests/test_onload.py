import time

from connection import Connection
from data_test import DataOnload as Data

session = 'marco'
con3 = Connection(node_name='k3d-p3')
con2 = Connection(node_name='k3d-p2')

assert Data.test_function_1 == con3.get('session-offloading-manager?command=test-function&type=session&value=' + session)
assert Data.test_function_2 == con2.get('session-offloading-manager?command=test-function&type=session&value=' + session)
# input("Press Enter to continue...")
time.sleep(1)

assert Data.force_offload == con3.get('session-offloading-manager?command=force-offload', 
                                      headers={'X-forced-session':'marco'})
# input("Press Enter to continue...")
time.sleep(1)

assert Data.test_function_3 == con3.get('session-offloading-manager?command=test-function&type=session&value=' + session)
assert Data.test_function_4 == con2.get('session-offloading-manager?command=test-function&type=session&value=' + session)
# input("Press Enter to continue...")
time.sleep(1)

assert Data.force_onload == con3.get('session-offloading-manager?command=force-onload')
# input("Press Enter to continue...")
time.sleep(1)

assert Data.test_function_5 == con3.get('session-offloading-manager?command=test-function&type=session&value=' + session)
assert Data.test_function_6 == con2.get('session-offloading-manager?command=test-function&type=session&value=' + session)
