const displayBrowserOption = () => {
    if (Alteryx.Gui.Manager.getDataItem('DoAutoViz').getValue()) {
        document.getElementById('displaybrowser').style.display = 'initial'
    } else {
        document.getElementById('displaybrowser').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {
  const operationSelectDatasetSource = new AlteryxDataItems.StringSelector('DataImportSource', {
    optionList: [
      {label: 'Filesystem', value: 'filesystem'},
      {label: 'Alteryx', value: 'upload'},
      {label: 'Azure Blob Store', value: 'azure'},
      {label: 'Amazon S3', value: 's3'},
      {label: 'Google Cloud Storage', value: 'gcs'}
    ]
  })
  operationSelectDatasetSource.setValue('upload')
  manager.addDataItem(operationSelectDatasetSource)
  manager.bindDataItemToWidget(operationSelectDatasetSource, 'DataSourceDropdown')
  displayBrowserOption()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayBrowserOption()
    Alteryx.Gui.Manager.getDataItem('DoAutoViz').registerPropertyListener('value', displayBrowserOption)
}